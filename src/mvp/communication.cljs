;;   Copyright (c) Dmitry Belikov. All rights reserved.
(ns fc.communication
  (:require
   [fc.mvp :as mvp]
   [fc.controller :as controller]
   [fc.util :as util]))

;;
;; Implements communication context and communication function.
;;
;; Communication function defines the actual tranmission protocol (AJAX or web-socket)
;; along with the actual serialization function (currently, native clojure serialization).
;;

(def jq (js* "$"))

(defn low-level-send
  "Implements the transmission function complying to the controller's logic."
  [content error-handler success-handler]
  (.ajax jq (mvp/clj->js
             {:cache false ;; do not cache the response
              :type "POST"
              :url "/api"
              :dataType "text" ;; used to be json
              :data (pr-str content)
              :success (fn [response status this]
                         (util/l-v "RECEIVED response:" [response status this])
                         (success-handler (cljs.reader/read-string response)))
              :error (fn [jqXHR textStatus errorThrown]
                       (util/l-v "AJAX POST failed with"
                                 [(.-status jqXHR) (.-statusText jqXHR)])
                       (error-handler))})))

(def socket (controller/create-communication-socket low-level-send))

(defn send-message
  "Schedules the specified message to be sent to the server. Adds context to the message.
   Automatically initiates transmission if the socket is idle."
  [{:keys [content response-handler time-out] :as message}]
  (controller/queue-message socket content (dissoc message :content))
  (controller/kick-off socket))
