;   Copyright (c) Dmitry Belikov. All rights reserved.
(ns fc.offlinetests
  (:require [fc.mvp :as mvp])
  (:use [fc.controller :only [create-communication-socket
                             queue-message
                             process-timed-out-messages
                             error-handler
                             success-handler
                             dispatch-response
                             kick-off]]
        [fc.util :only [l l-v get-now separate invoke-async clone-and-locate]]
        [fc.mvp-helper :only [monitor-mvp-dictionary bind-cookies]]
        [fc.timeline :only [schedule-in setup-rendering stop-timeline]]))

;; --================================= Offline UNIT TESTS ===========================--
(defn equal-json
  "Verifies that the provided two values are the same in JSON representation.
   ClojureScript adds some run-time information to types, this is a workaround to make
     comparison work."
  [o1 o2]
  #(js/equal
      (mvp/clj->json o1)
      (mvp/clj->json o2)))

(def jq (js* "$"))

(defn ^:export offlinetests []
  ;; verify qUnit
  (js/test "A basic test" #(js/ok true "is fine"))

  (js/test "equal is an assertion" #(js/equal "1234" "1234"))

  ;; --------------------------
  ;; utility calls do not fail
  (js/test "Check that utility calls do not crash"
           (fn []
             (l "Some simple message")
             (l-v "Current time" (get-now))
             (js/ok true "utility function called, no crashes")))

  (js/asyncTest "invoke-async, no pause"
                (fn []
                  (invoke-async (fn []
                                  (js/ok true "invoked asynchronously, no pauses!")
                                  (js/start)))))

  (js/asyncTest "invoke-async, 10 milliseconds"
                (fn []
                  (invoke-async
                   (fn []
                     (js/ok true "invoked as expected")
                     (js/start))
                   10)))

  (js/test "separate"
           (equal-json
            ['(-2 -1) '(0 1 2)]
            (separate #(< % 0) [-2 -1 0 1 2])))

  ;; --------------------------
  ;; low-level socket API
  (js/asyncTest "create-communication-socket, queue-message, dump-timed-out-messages"
                (fn []
                  (let [socket (create-communication-socket #(js/ok false "Transmission function called"))
                        timed-out-success (fn []
                                            (js/ok true "Successful timeout")
                                            (js/start))
                        timed-out-fail #(js/ok false "This method is not supposed to be called" )]
                    (queue-message socket {:a 1} {:response-handler timed-out-fail :timeout 60000})
                    (queue-message socket {:a 2} {:response-handler timed-out-success :timeout -60000})
                    (queue-message socket {:a 3} {:response-handler timed-out-fail})

                    (process-timed-out-messages socket)
                    (js/equal 2 2 (count (:message-queue @socket)) "remaining messages in the queue"))))

  ;; verify that low-level send calls retry in case of failure
  (js/asyncTest "error-handler: failure retry"
                (fn []
                  (let [socket (create-communication-socket #(js/ok false "Transmission function called"))
                        handler #(js/ok false "Handler not supposed to be called")
                        retry-func (fn []
                                     (js/ok true "retry function called")
                                     (js/equal 400 (:retry-timeout @socket))
                                     (js/equal :idle (:state @socket))
                                     (js/equal 2 (count (:message-queue @socket)))
                                     (js/start))]

                    (queue-message socket {:a 1} {:timeout 80000 :response-handler handler})
                    (queue-message socket {:b 2} {:response-handler handler})
                    (js/equal 2 (count (:message-queue @socket)))

                    (error-handler socket retry-func))))

  (js/asyncTest "dispatch-response: dispatching and final function invocation"
                (fn []
                  (let [socket (create-communication-socket #(js/ok false "Transmission function called"))
                        fn1 (atom false)
                        fn2 (atom false)
                        fn3 (atom false)
                        set-atom (fn [atom] (reset! atom true))]
                    (dispatch-response [[set-atom fn1] [set-atom fn2] [set-atom fn3]]
                                       #(do
                                          (js/ok @fn1 "Function 1 called")
                                          (js/ok @fn2 "Function 2 called")
                                          (js/ok @fn3 "Function 3 called")
                                          (js/start))))))

  (js/asyncTest "dispatch-response: correctly swallows response-handler exceptions"
                (fn []
                  (let [socket (create-communication-socket #(js/ok false "Transmission function called"))
                        fn1 (atom false)
                        set-atom (fn [atom] (reset! atom true))]
                    (dispatch-response [[#(throw (js/Error. "something")) nil] [set-atom fn1]]
                                       #(do
                                          (js/ok @fn1 "Function 1 called")
                                          (js/start))))))

  ;; verify that low-level send successfully cleans the sent messages
  ;; verify that sent messages disappeared from the queue
  ;; their response-handler called even if answer not provided
  (js/asyncTest "success-handler: successful send"
                (fn []
                  (let [socket (create-communication-socket #(js/ok false "Transmission function called"))
                        counter (atom 0)
                        increase-counter (fn [n] (swap! counter (partial + n)))
                        handler-failure (fn [ignored] (js/ok false "Handler not supposed to be called"))]

                    ;; answer will be provided
                    (queue-message socket {:a 1} {:timeout 80000 :response-handler increase-counter})
                    ;; time-out long elapsed, but successfully invoked
                    (queue-message socket {:b 2} {:timeout -80000 :response-handler increase-counter})
                    ;; no handler, so it won't be called
                    (queue-message socket {:c 3} {:timeout 80000})
                    ;; was not sent
                    (queue-message socket {:d 4} {:response-handler handler-failure})
                    (js/equal 4 (count (:message-queue @socket)) "4 messages in the queue")

                    (let [existing (take 3 (map (fn [{:keys [guid]} number] {:guid guid :response number})
                                                (:message-queue @socket)
                                                (range 10 100)))
                          with-not-existing (vec (cons {:guid "non-existing-guid" :response :a} existing))]

                      (success-handler
                       socket

                       with-not-existing

                       #(do
                          (js/ok (= :idle (:state @socket)) "socket must be in idle state to send messages")
                          (js/equal 1 (count (:message-queue @socket)) "one message not received")
                          (js/equal 21 @counter)
                          (js/start)))))))

  (js/test "kick-off does nothing unless socket is in :idle state"
           (fn []
             (let [socket (create-communication-socket #(js/ok false "Transmission function called"))]
               (swap! socket #(assoc % :state :waiting-response))
               (kick-off socket)
               (js/expect 0))))

  (js/test "low-level-send comes up with messages and initiate sending"
           (fn []
             (let [send-function (fn [content error success]
                                   (l-v "content" content)
                                   (js/equal 2 (count content)))
                   socket (create-communication-socket send-function)

                   not-called #(js/ok false "not supposed to be called")]

               (queue-message socket 1 {:response-handler not-called})
               (queue-message socket 2 {:response-handler not-called})
               (kick-off socket))))

  (js/test "clone-and-locate returns correct map if inner elements"
           (fn []
             (let [[html m] (clone-and-locate (jq "#clone-and-locate-sample-data"))]

               (js/ok (get m "clone-and-locate-sample-data") "root present")
               (js/ok (get m "inside_element") "inside_element present")
               (js/ok (get m "child") "child present")
               (js/ok (get m "second") "second present")
               (js/ok (not (get m "non-existing")) "non-existing missing")
               (js/equal 4 (count m)))))

  (js/test "monitor-mvp-dictionary"
           (fn []
             (let [model (mvp/create {:e {:a 12 :b 15}})
                   marked (atom #{})
                   callback (fn [m p]
                              (js/ok (== m model))
                              (swap! marked #(conj % p)))]

               (monitor-mvp-dictionary model [:e] callback)
               (js/ok (= @marked #{[:e :a] [:e :b]}) "initial set of element was reported")

               (mvp/assoc-value model [:e :c] 16)
               (js/ok (= @marked #{[:e :a] [:e :b] [:e :c]}) "changed set")

               (mvp/assoc-value model [:e :a] 16)
               (js/ok (= @marked #{[:e :a] [:e :b] [:e :c]}) "same set"))))

  (js/test "bind-cookies"
           (fn []
             (let [cookie-name "test-cookie-1"
                   model (mvp/create {:e "irrelevant"})
                   binder1 (bind-cookies cookie-name model [:e])]

               ;; Verify that model's value changed after binding.
               (js/ok (not= "irrelevant" (mvp/get-value model [:e])) "changed after binding")

               ;; Change the model and see the change through the second binder.
               (mvp/assoc-value model [:e] "value12")
               (bind-cookies cookie-name model [:g])
               (js/ok (= "value12" (mvp/get-value model [:g])) "change propagated")

               ;; Verify map serialization
               (mvp/assoc-value model [:g] {:a [1 2]})
               (bind-cookies cookie-name model [:k])
               (js/ok (= {:a [1 2]} (mvp/get-value model [:k])) "change propagated"))))

  (js/asyncTest "timeline: render function call and 3-sec scheduled event"
                (fn []
                  (let [render-called (atom false)]
                    (setup-rendering #(do (reset! render-called true)))
                    (schedule-in 1000 #(do
                                         (js/ok @render-called "Scheduled event called")
                                         (stop-timeline)
                                         (js/start)))))))
