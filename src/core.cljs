(ns hello.core
  (:require
    [clojure.browser.repl :as repl]
    [clojure.browser.dom :as cdom]
    [clojure.string :as cstring]
    [goog.object :as goog-object]
    [goog.events.EventType :as goog-event-type]
    [goog.ui.ColorButton :as color-button]
    [goog.ui.Tab :as gtab]
    [goog.string :as gstring]
    [goog.crypt.base64 :as base64]
    [goog.ui.TabBar :as gtabb]
    [mvp.model :as mvp]))

(repl/connect "http://localhost:9000/repl")

(defn ^{:export greet} greet [n]
  (str "Helloprivet " n))

(defn ^:export sum [xs]
  (reduce + 0 xs))

;; -------------------------= State =-----------------------
(def database-connected? (atom false))
(def messaging-connected? (atom false))

;; --------------------------= Widgets =-----------------------------
(def database-button
  (doto (goog.ui.ColorButton. "Database")
    (.setTooltip "Database Connection Status")
    (.setValue "red")))

(def messaging-button (doto (goog.ui.ColorButton. "Messaging")
                        (.setTooltip "Messaging Connection Status")
                        (.setValue "red")))

(def tabbar (goog.ui.TabBar.))

;; -----------------------= Control =---------------------
;; Event handling for the UI
(def events (.getValues goog.object goog.ui.Component/EventType))

(defn handle-tab-select [tabbar e]
(let [tab (.target e)
      content (.getElement goog.dom (str (. tabbar (getId)) "_content"))]
  (.setTextContent goog.dom content (. tab (getCaption)))))

(defn button-color
"Associate a colour with the states"
[x]
(if x "green" "red"))

(defn toggle-state!
"Simulate trivial connect/disconnect functionality for the buttions"
[x]
(swap! x not))

(defn handle-button-push [s e]
  (.setValue (.target e)
             (button-color (toggle-state! s))))

;; ---------------------= MAIN =------------------------
;; Main entry function
(defn ^:export main []
  ;; Populate a DOM via decoration.
  (.decorate tabbar (.getElement goog.dom "top"))

  ;; Populate a DOM via rendering.
  (.render database-button (.getElement goog.dom "buttons"))
  (.render messaging-button (.getElement goog.dom "buttons"))

  ;; Wire up the events
  ;; The tabbar event
  (.listen goog.events
           tabbar
           goog.ui.Component.EventType/SELECT
           (partial handle-tab-select tabbar))

  ;; The database button
  (.listen goog.events
           database-button
           goog.ui.Component.EventType/ACTION
           (partial handle-button-push
                    database-connected?))

  ;; The messaging button
  (.listen goog.events
         messaging-button
         goog.ui.Component.EventType/ACTION
         (partial handle-button-push
                  messaging-connected?)))

;; ---------------------= Direct Javascript =------------------------
; new Date
(js/Date.)
(js/eval "new String('2+2')")

;; ---------------------= jQuery =------------------------
(def jquery (js* "$"))
(. (jquery (js* "document")) ready
   (fn []
     (. (jquery "span.meat") html "This is a test.")))

;(. (js/$) )

(. (jquery (js* "document")) ready
   (fn []
     (-> (jquery "div.meat")
         (.html "This is a test.")
         (.append "<div>Look here!</div>"))))

                                        ; (js/alert "here")

(def j js/jQuery)

(.text (j "div#foo") "jQuery works!")

;; ---------------------= Closure =------------------------
(comment
 (.getElement goog.dom "foo")  ; gets HTMLDivElement (DOM element)
 (let
     [fooDiv (.getElement goog.dom "foo")]
   (.setTextContent goog.dom fooDiv "Some other content"))

 ;; setting property on goog.dom.getElement("foo").innerHTML = "Content23"
 (let
     [fooDiv (.getElement goog.dom "foo")]
   (set! (.innerHTML fooDiv) "Content23"))

 ;; get a property
 (let
     [fooDiv (.getElement goog.dom "foo")]
   (.innerHTML fooDiv))

 (let [x1 [89 72 94 69]
       min (apply min x1)
       max (apply max x1)
       range (- max min)
       ave (/ (apply + x1) (count x1))]
   { :min min, :max max, :range range, :ave ave, :normalized (/ (- 89 ave) range) })
 (min 89 72 94 69))


;(def foo
;  (.getElement goog.dom "foo"))

                                        ;(.getUid goog)

(goog.getUid 12)

(goog.getMsg "SomeString {$f} here" ["f" "there"] {"f" "there", "f2" "up there"})

{"key" "value"}

(defn make-js-map
  "makes a javascript map from a clojure one"
  [cljmap]
  (let [out (js-obj)]
    (doall (map #(aset out (name (first %)) (second %)) cljmap))
    out))

(make-js-map {"f" "there", "f2" "here"})

(goog.getMsg
 "SomeString {$f} here"
 (make-js-map {"f" "there", "f2" "up there"}))

(goog.nullFunction 2 34)

;; abstract method
(comment goog.abstractMethod)

(goog.now)

(goog.string.htmlEscape "a string <a>")
(goog.string.urlEncode "encoded & URL = there! <>")

; "{\"meta\":null,\"keys\":[\"a\",\"b\"],\"strobj\":{\"a\":\"a\",\"b\":\"b\"}}"
(goog.json.serialize {"a" "a" "b" "b"})

; "{\"f\":\"there\",\"f2\":\"here\"}"
(goog.json.serialize (make-js-map {"f" "there", "f2" "here"}))
(goog.json.serialize (make-js-map {"f" "there", "f2" ["there" "here"]}))

;(clojure.string/escape "this <a> & a string")
(string? "asdf")

(str "asdf" "qwer")

(replace "asd" "a")
(clojure.string/join "-" '("qwer" "aasdf"))

(base64/encodeString "asdfq23#@FQ@#")

(cdom/log "message1" 23 25)

(cdom/element "span" {:id "dif" :class "element"})
(cdom/get-element "foo")
(cdom/insert-at
 (cdom/get-element "foo")
 (cdom/element "span" {:id "dif" :class "element"} "someVal") 0)

(defn clj->js
  "Recursively transforms ClojureScript maps into Javascript objects,
   other ClojureScript colls into JavaScript arrays, and ClojureScript
   keywords into JavaScript strings."
  [x]
  (cond
   (string? x) x
   (keyword? x) (name x)
   (map? x) (.strobj (reduce (fn [m [k v]]
                               (assoc m (clj->js k) (clj->js v))) {} x))
   (coll? x) (apply array (map clj->js x))
   :else x))

(goog.json.serialize
 (clj->js {"a" "b"}))

(goog.json.serialize
 (clj->js {"a" ["c" "b"]}))

(goog.json.serialize
 (clj->js {:at 12 :before ["c" "b"]}))

(goog.json.serialize
 (clj->js {:at 12 :before ["c" "b"] :inner {:r 34} :inner-array [{:a :b} {:c :d}]}))

(js->clj
 (clj->js {:at 12 :before ["c" "b"] :inner {:r 34} :inner-array [{:a :b} {:c :d}]}))


(defn json-generate
  "Returns a newline-terminate JSON string from the given
   ClojureScript data."
  [data]
  (str (JSON/stringify (clj->js data)) "\n"))

(defn json-parse
  "Returns ClojureScript data for the given JSON string."
  [line]
  (js->clj (JSON/parse line)))


(comment
  (cdom/insert-at
   (cdom/get-element "foo")
   (cdom/element "span" {:id "dif" :class "element"} "someVal") 0)

  (cdom/insert-at
   (cdom/get-element "bar")
   (cdom/get-element "foo")
   0)

  (goog.dom.append
   (cdom/get-element "bar")
   (cdom/get-element "bar")
   (cdom/get-element "foo")
   1)
  )

(cdom/insert-at
 (cdom/get-element "bar")
 (.cloneNode
  (cdom/get-element "foo")
  true)
 0)



;; MVP implementation



(mvp/create-mvp 34)

(js/test "A basic test" #(js/ok true "is fine"))
