;;   Copyright (c) Dmitry Belikov. All rights reserved.
;;
(ns fc.mvp-helper
  (:use [clojure.set :only [difference]]
        [fc.util :only [l-v jq suppress-exceptions]])
  (:require [fc.mvp :as mvp]
            [fc.communication :as com]
            [cljs.reader :as cljsreader]
            [goog.net.cookies :as cookies]))

;; Implements binders, listeners, etc to tie together HTML elements and MVP models
;; =============================================================================================
;; anything == selector, HTML element, JQuery object
;;
;; HTML text:
;; (bind-contenteditable anything mvp-model path)
;; (bind-textarea anything mvp-model path)
;; (bind-text anything mvp-model path) .text() in jQuery
;; (show-when element model path (condition new-value)) show on codition; otherwise, hide.
;;   (h/show-when (get elements "learning_term") model [:phase] #(in? '(:question :no-answer)))
;; (show-and-focus-when element model path (condition new-value)) to show and focus or hide
;;
;; MVP list manager:
;; (monitor-mvp-dictionary mvp-model mvp-path (callback mvp-model full-mvp-path-to-new-element))
;;
;; Cookies:
;; (bind-cookies "cookie-name" mvp-model [:path :in :mvp])

(defn- on-contenteditable-change
  "Calls the specified function each time the content of the contenteditable element gets
   changed. The whole element's HTML sub tree is being tracked for changes."
  [element func]
  (let [trigger-change (fn []
                         (if (not= (.html element) (.data element "before"))
                           (do
                             (.data element "before" (.html element))
                             (func))))
        store-current #(.data element "before" (.html element))]

    (.on element "focus" store-current)
    (.on element "blur keyup paste" trigger-change)))

(defn bind-contenteditable
  "Binds the specified contenteditable jquery element onto the MVP model at the specified path."
  [raw-element mvp-model path]
  (let [element (jq raw-element)]
    (mvp/add-reader mvp-model path
                    (fn [{:keys [new-value]}]
                      (if (not= (.text element) new-value)
                        (.text element new-value))))

    (on-contenteditable-change element
                               #(mvp/assoc-value mvp-model path (.text element)))))

(defn bind-checkbox
  "Binds the specified checkbox onto the MVP model boolean entry."
  [jq-selector mvp-model path]
  (let [jq-element (jq jq-selector)]
    (mvp/add-reader mvp-model
                    path
                    (fn [{:keys [new-value]}]
                      (if (not= (.prop jq-element "checked") new-value)
                        (.prop jq-element "checked" new-value))))

    (.on jq-element "change"
         #(mvp/assoc-value mvp-model path (.prop jq-element "checked")))))

;;(h/bind-textarea (get elements "learning_term") model [:current-term :input])
(defn bind-textarea
  "Binds the specified textarea onto the MVP model at the specified path."
  [element mvp-model path]
  (let [jq-element (jq element)]
    (mvp/add-reader mvp-model
                    path
                    (fn [{:keys [new-value]}]
                      (if (not= (.val jq-element) new-value)
                        (.val jq-element new-value))))

    (.bind jq-element "input propertychange" #(mvp/assoc-value mvp-model path
                                                               (.val jq-element)))))

(comment defn bind-serialized-textarea
  "Textarea will display serialized clojure data."
  [element mvp-model path]
  (let [jq-element (jq element)]
    (mvp/add-reader mvp-model
                    path
                    (fn [{:keys [new-value]}]
                      (let [serialized-value (clojure.string/replace (pr-str new-value) "{"
                                                                     "\n{")]
                        (if (not= (.val jq-element) serialized-value)
                          (.val jq-element serialized-value)))))

    (.bind jq-element
           "input propertychange"
           #(if-let [deserialized-val
                     (suppress-exceptions (fn [] (cljsreader/read-string (.val jq-element))))]
              (mvp/assoc-value mvp-model path deserialized-val)))))

;;(.text (jq (get elements "learning_description")) "here goes the new description
;;                                                   instead of question")
(defn bind-text
  "Binds the text property of the specified element (anything) onto the MVP model."
  [element mvp-model path]
  (let [jq-element (jq element)]
    (mvp/add-reader mvp-model
                    path
                    (fn [{:keys [new-value]}]
                      (if (not= (.text jq-element) new-value)
                        (.text jq-element new-value))))))

(defn monitor-mvp-dictionary
  "Monitors MVP dictionary for new elements. Each time a new element is detected,
   The specified function is being invoked with MVP-model and full-mvp-path as parameters."
  [model path on-new-element]
  (let [known (atom #{})
        reader (fn [{:keys [new-value]}]
                                 (l-v "reader invoked for" new-value)
                                 (let [new-keys (set (keys (mvp/get-value model path)))
                                       diff (difference new-keys @known)]
                                   (dorun (map #(on-new-element model (conj path %)) diff))
                                   (reset! known new-keys)))]
    (mvp/add-reader model path reader)))

;; (h/show-when (get elements "learning_term") model [:phase] #(in? '(:question :no-answer)))
(defn show-when
  "Displays the provided element (can be a selector, HTML element, JQuery object, anything)
   when the specified condition is true on the value taken from the MVP model."
  [element model path condition]
  (mvp/add-reader model path
                  (fn [{:keys [new-value]}]
                    (if (condition new-value)
                      (.show (jq element))
                      (.hide (jq element))))))

(defn show-and-focus-when
  "Displays the provided element (can be a selector, HTML element, JQuery object, anything)
   when the specified condition is true on the value taken from the MVP model."
  [element model path condition]
  (mvp/add-reader model path
                  (fn [{:keys [new-value]}]
                    (if (condition new-value)
                      (do
                        (.show (jq element))
                        (.focus (jq element)))
                      (.hide (jq element))))))

(def two-months-in-sec (* 60 60 24 62))

(defn bind-cookies
  "Binds MVP onto cookies container: load from cookie at binding and persiste at any change."
  [cookie-name model path]
  (mvp/assoc-value model path (cljs.reader/read-string (cookies/get cookie-name)))
  (mvp/add-reader model path (fn [{:keys [new-value]}]
                               (cookies/set
                                cookie-name (pr-str new-value) two-months-in-sec))))
