;; Copyright (c) Dmitry Belikov. All rights reserved.
;; This file exposes ClojureScript utility methods, primarily encapsulating 3rd party API.

;; Included utility methods:
;;
;; (l "this is to be logged in the browser")
;; (l-v "name" value)
;; ((create-logger-panel) "My message to be put into created log panel at the bottom")
;; (get-now) => milliseconds
;;
;; (invoke-async (fn [] ...))
;; (invoke-async (fn [] ...) in-milliseconds)
;;
;; (separate (fn [element] true) sequence) => [ (filter f s), (filter (complement f) s) ]
;; (in? collection value)
;; (not-in? collection value)
;; (delete-at vector index) => vector without that element
;;
;; (clone-and-locate anything) => [cloned-HTMLElement {:tag1 HTMLElement :tag2 HTMLElement} ]
;;
;; (on-click anything (fn [] ...)) => subscribes and supresses default action
;;
;; (capture-exception (fn [] ...)) => nil or raised exception
;; (supress-exception (fn [] ...)) => value of the function or nil if exception

(ns fc.util
  (:require [clojure.browser.dom :as cdom]))

(def jq (js* "$"))

(defn l
  "Logs the message into the browser log."
  [message]
  (cdom/log message))

(defn l-v
  "Logs clojure value"
  [name value]
  (l (str name ": " (pr-str value))))

(defn get-now
  "Gets the current time moment as amount of milliseconds from some constant point in the past."
  []
  (.getTime (js/Date.)))

(defn invoke-async
  "Invokes the specified function in a separate JS execution slot
   by scheduling its execution from JS timer in 1 millisecond from now."
  ([invocation]
     (.setTimeout js/window invocation 1))
  ([invocation milliseconds]
     (.setTimeout js/window invocation milliseconds)))

(defn separate
  "Returns a vector: [ (filter f s), (filter (complement f) s) ]"
  [f s]
  [(filter f s) (filter (complement f) s)])

(defn in?
  "True if the specified value is in the collection."
  [coll val]
  (some #(= val %) coll))

(defn not-in?
  "True if the specified value is not in the collection."
  [coll val]
  (not (in? coll val)))

(defn create-logger-panel
  "Creates a logger at the bottom of the page and
   answers a function that appends a message to the logger."
  []
  (.append (jq "body") "<div id=\"log_panel\" style=\"height:200px;overflow-x:hidden;overflow-y:auto;background-color: #FFF;color: #000;font: 0.75em/1.3;\"></div>")

  (let [panel (jq "#log_panel")
        panel-element (aget panel 0)]
    (fn [message]
      (.append panel (str message "<br>"))
      (set! (.-scrollTop panel-element) (.-scrollHeight panel-element))
      message)))

(defn clone-and-locate
  "Clones the specified element, cleans all IDs on the cloned elements and returns a map
   of the IDs mapped to their respective elements in the cloned instance."
  [anything]

  (let [copy (.clone (jq anything) false)
        html (aget copy 0)
        m (atom {})
        check-id (fn [e]
                   (.removeClass (jq e) "template")
                   (if (seq (.-id e))
                           (do
                             (swap! m #(assoc % (.-id e) e))
                             (set! (.-id e) nil))))
        _ (check-id html)
        _ (.each (.find copy "*") (fn [i e] (check-id e)))]
    [html @m]))

(defn on-click
  "Invokes the specified function when the element is clicked."
  [anything click-handler]
  (.click (jq anything)
          (fn [element]
            (.preventDefault element) ;; to prevent event propagation
            (click-handler))))

(defn capture-exception
  "Invokes the specified function and returns an exception if it was raised.
   Null is returned if function didn't result in an exception."
  [function]
  (js/swallowEverything function))

(defn suppress-exception
  "Invokes the specified function and swallows any possible exceptions.
   Answers the value of the function or nil if exception occurred."
  [function]
  (js/exceptionToNull function))

(defn delete-at
  "Deletes an element from a vector at the specified index."
  [v index]
  (vec (concat (take index v) (drop (inc index) v))))
