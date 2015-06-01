;   Copyright (c) Dmitry Belikov. All rights reserved.
(ns fc.controller
  (:require
    [fc.mvp :as mvp])
  (:use [fc.util :only [l l-v get-now invoke-async separate in? not-in?]]))

;; --============================ Communication Controller Functionality =====================--
;;
;; main scenarios:
;; 1. user is blocked on specific message => strict trackable time-out
;; 2. user must get confirmation (eventually) => no time-out
;; 3. user doesn't care if message delivered => no time-out
;;
;; for scenarios where data needs to be sync'ed and it can get sync'ed in the next session
;; it's a matter of data version comparison and resolution algorithm, not covered by controller.
;;
;; user context and authentication is not covered by controller
;; current implementation sends all current messages at once
;; no priority system: all messages will be delivered sequentially and processes strictly
;;   sequentially
;; all messages must be idempotent for both client and server
;; each response handler is triggered in a separate tick
;; all exceptions out of response handlers are ignored
;;
;; ALGO:
;; 1. kick-off
;;    if not in idle state, exit
;;    serialize messages, send to the server, set state to :in-progress
;;    MVP status :sending
;;
;; 2. failure handle called
;;    double current-failure-timeout (min maximum-timeout current-failure-timeout)
;;    invoke-async in that timeout
;;    set status to :idle
;;    MVP status: :fail
;;    call (kick-off)
;;
;; 3. success handle called
;;    MVP status: :received
;;    process-first-message
;;    invoke-async 1
;;    recursively process all messages with invoke-async in between
;;    remove sent messages from the queue
;;    set status to :idle
;;    MVP status: :idle
;;    (invoke-async)
;;    (kick-off)
;;
;; WARNING: queue-message doesn't call kick-off automatically,
;;          this is done by communication engine
;;
;; All messages to the server are sent sequentially as a '(message1 message2 message3 ...)
;; where each message is {:guid GUID :message CONTENT}
;;
;; All responses are received as [ [GUID CONTENT] [GUID nil] [GUID CONTENT] ... ]
;; and processed sequentially as well.
;;

;; TODO: test timeout handlers via sleep on the server
;; TODO: timer for timed-out messages?!
;; TODO: server overload if sent messages & successful response doesn't
;;       contain a guid for that message

(def initial-time-out 200)            ; 200 milliseconds
(def max-time-out 30000)

(defn create-communication-socket
  "Creates a communication socket to communicate with the server by the means
   of the transmission function.
   (transmission-function content-to-send (error-handler) (success-handler response))"
  [transmission-function]
  (let [mvp-model (mvp/create {:status :idle})]
    (atom {:state :idle ; :waiting-response :timeout
           :message-queue [] ; [{:guid :message :timeout :response-handler}]
           :status mvp-model
           :retry-timeout initial-time-out
           :transmission-function transmission-function})))

(defn process-timed-out-messages
  "Invokes handlers of the timed-out messages and removes them from the queue."
  [socket]
  (swap! socket
         (fn [socket-value]
           (let [now (get-now)
                 [timed-out ok] (separate
                                 (fn [{:keys [timeout]}] (and timeout (> now timeout)))
                                 (:message-queue socket-value))]

             ;; schedule invocation of timed-out functions
             (doseq [{:keys [response-handler]} timed-out]
               (if response-handler
                 (invoke-async response-handler)))

             (assoc socket-value :message-queue ok)))))

(defn error-handler
  "Messagess failed to be sent, initiate re-try after time-out."
  [socket retry-function]
  (swap! socket ; send has failed, go into waiting mode for the current timeout
         (fn [{:keys [status retry-timeout] :as socket-value}]

           ;; TODO: update MVP model to output the timeout state

           (l-v "Scheduling retry in" retry-timeout)

           (invoke-async
            (fn []
              (swap! socket #(assoc % :state :idle))
              (retry-function))
            retry-timeout)

           (assoc socket-value
             :state :timeout
             :retry-timeout (min max-time-out (* 2 retry-timeout))))))

(defn dispatch-response
  "Dispatches responses to the specified functions asyncronously, supressing any exceptions.
   Calls the continuation once all responses are dispatched"
  [[[response-handler response-parameter] & remaining-responses] continuation]

  (if response-handler
    (do
      (let [exception (js/swallowEverything #(response-handler response-parameter))]
        (if exception
          (l-v "Response handler resulted in exception:" exception)))

      (invoke-async #(dispatch-response remaining-responses continuation)))
    (continuation)))

(defn success-handler
  "Responses to messages are received, dispatch them to handlers and drop from the queue."
  [socket response retry-function]

  (let [{:keys [message-queue]} @socket]

    ;; split message queue into received-part and not-received
    (let [received-guids (map :guid response)
          received-part (filter #(in? received-guids (:guid %)) message-queue)
          received-messages-map (apply hash-map
                                       (mapcat (fn [{:keys [guid response-handler]}]
                                                 [guid response-handler])
                                               (filter :response-handler received-part)))
          handler-invocations (filter first
                                      (map (fn [{:keys [guid response]}]
                                             [(get received-messages-map guid) response])
                                           response))]

      (dispatch-response
       handler-invocations
       (fn []
         ;; TODO: update MVP-model
         (swap! socket #(assoc %
                          :message-queue (filter (fn [e] (not-in? received-guids (:guid e)))
                                                 (:message-queue %))
                          :retry-timeout initial-time-out
                          :state :idle))
         (retry-function))))))

(defn kick-off
  "If the socket is in idle state, initiate transmission of queued messages."
  [socket]

  (process-timed-out-messages socket)

  (let [messages-to-send (atom nil)]
    (swap! socket
           (fn [{:keys [state message-queue status] :as socket-value}]
             (if (and (= :idle state) (> (count message-queue) 0))
               (let [messages
                     (map (fn [{:keys [guid message]}] {:guid guid :message message})
                          message-queue)]

                 (reset! messages-to-send messages)
                 ;; TODO: update MVP model here to :sending
                 (assoc socket-value :state :sending))
               socket-value)))

    (if-let [messages @messages-to-send]
      (if (> (count messages) 0)
       ((:transmission-function @socket)
        messages
        #(error-handler socket (fn [] (kick-off socket)))
        #(success-handler socket % (fn [] (kick-off socket))))))))

(defn queue-message
  "Queues the specified message (a clojure object which is converted into JSON).
   The following properties are recognized:
      :timeout Optional timeout in milliseconds, the response handler is invoked
               with no arguments in case of timeout.
      :response-handler Optional function that will handle the response; it either receives one
               argument in case of a proper response or no arguments to handle timeouts."
  [socket message {:keys [response-handler timeout]}]

  (l-v "queued message" message)

  (swap! socket
         (fn [socket-value]
           (let [guid (mvp/generate-unique-version)
                 queued-message {:guid guid :message message}
                 with-timeout (if timeout
                                (assoc queued-message :timeout (+ (get-now) timeout))
                                queued-message)
                 with-handler (if response-handler
                                (assoc with-timeout :response-handler response-handler)
                                with-timeout)]

             (assoc socket-value :message-queue
                    (conj (:message-queue socket-value) with-handler))))))
