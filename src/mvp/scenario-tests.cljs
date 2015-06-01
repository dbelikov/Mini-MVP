;   Copyright (c) Dmitry Belikov. All rights reserved.
(ns fc.scenario-tests
  (:require [fc.mvp :as mvp]
            [fc.communication :as com]
            [fc.util :as u]))

(defn ^:export scenario-tests []
  ;; verify qUnit
  (js/test "Scenario test #1" #(js/ok true "is fine"))

  (js/asyncTest "Make a simple call and verify that response handler gets invoked"
           (fn []
             (com/send-message {:content {:fn :plus-twenty :value 167}
                                :response-handler (fn [result]
                                                    (js/equal 187 result)
                                                    (js/ok true
                                                           "sent and response handler called")
                                                    (js/start))})))

  (comment js/asyncTest "Save/load value, verify it's correct"
                (fn []
                  (let [verify-loaded-entry (fn [result]
                                              (js/equal "198" result)
                                              (js/ok true "sent and response handler called")
                                              (js/start))

                        load-message (fn [ignored]
                                       (u/l-v "load-message called" ignored)
                                       (com/send-message {:content {:fn :load-entry
                                                                    :pk "test-entry"}
                                                          :response-handler
                                                          verify-loaded-entry}))]

                    (com/send-message {:content {:fn :save-entry :pk "test-entry" :value "198"}
                                       :response-handler load-message})))))
