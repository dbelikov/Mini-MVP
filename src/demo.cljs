;   Copyright (c) Dmitry Belikov. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php).
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns minimvp.demo
  (:require
    [clojure.browser.repl :as repl]
    [clojure.browser.dom :as cdom]
    [clojure.string :as cstring]
    [goog.object :as goog-object]
    [goog.events :as goog-events]
    [goog.events.EventType :as goog-events-type]
    [goog.ui.Component.EventType :as goog-ui-component-eventtype]
    [goog.ui.ColorButton :as color-button]
    [goog.ui.LabelInput :as glabel]
    [goog.ui.Tab :as gtab]
    [goog.string :as gstring]
    [goog.crypt.base64 :as base64]
    [goog.ui.TabBar :as gtabb]
    [goog.ui.Textarea :as gtextarea]
    [minimvp.core :as mvp]))

(repl/connect "http://localhost:9000/repl")

(defn ^:export demo []

  (def jq (js* "$"))
  
  ;; text -----------------------------------------------
  (def text-model (mvp/create { :text "text box sample text" :hint "text box hint" }))


  ;; text/google Closure
  (def text (mvp/create-ref text-model [:text]))

  (def goog-text (goog.ui.LabelInput.))
  (.decorate goog-text (.getElement goog.dom "text1") )

  (mvp/add-reader text-model [:hint] #(.setLabel goog-text (:new-value %)))
  (mvp/add-reader text-model [:text] #(.setValue goog-text (:new-value %)))
  (.listen goog.events
           (.getElement goog.dom "text1")
           goog.ui.Component.EventType/CHANGE
           #(mvp/assoc-value text [] (.getValue goog-text nil) ))

  ;; textarea/google Closure
  (def text-area-div (.getElement goog.dom "text"))

  (def goog-text-area
    (doto (goog.ui.Textarea. "Google-TextArea")
      (.setValue "some default value")))
  (.render goog-text-area text-area-div)

  (mvp/add-reader text-model [:text] #(.setValue goog-text-area (:new-value %)))
  (.listen goog.events
           (. goog-text-area (getElement))
           goog.ui.Component.EventType/CHANGE
           #(mvp/assoc-value text [] (.getValue goog-text-area nil) ))

  ;; jquery text control
  (.val (jq "#text2") "wqerqwer")
  (.val (jq "#text2") "wqerqwer23")
  (. (jq "#text2") (val) )

  (mvp/add-reader text-model [:text] #(.val (jq "#text2") (:new-value %)))
  (.change (jq "#text2") #(mvp/assoc-value text [] (. (jq "#text2") (val))))

  ;; custom span control/goog
  (def control1 (goog.ui.Control. (.createDom goog.dom "span" nil "Hello, world!")))
  (.render control1 text-area-div)
  (mvp/add-reader text-model [:text] #(.setContent control1 (:new-value %)))

  
  ;; TODO: check boxes -----------------------------------------------------

  ;; TODO: composite/collection binding ------------------------------------

  ;; functional demo -------------------------------------------------------
  (def my-model (mvp/create { :type Text :hint "Enter your age" :value "1234" :error "Value too big to be true" }))

  (mvp/add-reader my-model [:value] #(.val (jq "#input-control") (:new-value %)))
  (mvp/add-reader my-model [:error] #(.html (jq "#error-message") (:new-value %)))

  ;; generic input validation
  (defn- age-validator
    "Answers a text error message if the specified age is out of valid range.
     Yields an empty string in case if the specified age appears to be correct."
    [age]
    (if (> age 120) "Value too big to be true" ""))

  ;; bind input validation to age changes
  (mvp/add-writer
   my-model  ;; we subscribe to changes in my-model
   [:value]  ;; at path ROOT -> :value
   ;; and when a value under ROOT->:value changes, we update the value at ROOT->:error
   #(mvp/assoc-value my-model [:error] (age-validator (:new-value %))))

  (.change (jq "#input-control") #(mvp/assoc-value my-model [:value] (. (jq "#input-control") (val))))
  (.keyup (jq "#input-control") #(mvp/assoc-value my-model [:value] (. (jq "#input-control") (val))))
)
