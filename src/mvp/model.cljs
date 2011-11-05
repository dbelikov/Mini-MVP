;;   Copyright (c) Dmitry Belikov. All rights reserved.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php).
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.
(ns minimvp.core
  (:require
   [clojure.browser.dom :as cdom]))

(defn- iterate-while
  "Executes the specified function against each element in the sequence as long as the condition is met.
   Answers true if all elements were processed. Answers false if the loop was broken due to false condition."
  [f coll condition]
  (if (condition)
    (if (seq coll)
      (do
        (f (first coll))
        (recur f (next coll) condition))
      true)
    false))

(defn fail-loudly
  "Fails loudly to ease debugging"
  [message]
  (cdom/log message)
  (js/alert message))

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

(defn clj->json
  "Transforms a clojure object into a JSON string"
  [model]
  (goog.json.serialize (clj->js model)))

(defn- int->char
  "Converts an integer into the corresponding ASCII character"
  [n] (.fromCharCode js/String n))

(defn- get-random-char
  "Answers a random character from 32-127 ANSI range each time it is called"
  [] (int->char (Math/floor (+ (* (Math/random) (- 125 32)) 33))))

(defn- generate-unique-version
  "Generates a random string that is highly unlikely to be ever reproduced"
  [] (apply str (take 16 (repeatedly get-random-char))))

(defn create
  "Wraps up the value into basic MVP structure"
  ([model version versioning]
     {
      :model (atom [version model])
      :versioning versioning
      :writers (atom [])
      :readers (atom [])
      :scanning (atom false)
      })
  ([model]
     (create model (generate-unique-version) generate-unique-version)))

(defn create-ref
  "Creates a reference to a particular part of an MVP model"
  [mvp base-path]
  (list mvp base-path))

(defn- get-mvp-model
  "Retrieves the current model out of the MVP structure"
  [mvp]
  (second @(:model mvp)))

(defn get-value
  "Gets the value from the MVP model. It can be a regular value or a nested MVP model"
  ([mvp path]
     (if (sequential? mvp)
       (recur (first mvp) (into (second mvp) path))
       (get-in (get-mvp-model mvp) path)))
  ([mvp path default]
     (let [fetched-value (get-value mvp path)]
       (if (nil? fetched-value)
         default
         fetched-value))))

(defn- increment-version
  "Suggests the next version for the specified MVP object"
  [mvp]
  ((:versioning mvp) (first @(:model mvp))))

(defn- set-value-basic
  "Attempts to updates the MVP model at the specified path if the current value at that path is not equal
   to the specified new value."
  [mvp path new-value]
  (if (empty? path)
    ;; root-update scenario, only update root if not equal
    (if (not= (get-mvp-model mvp) new-value)
      (reset! (:model mvp) [(increment-version mvp) new-value]))

    ;; generic inner update
    (let [old-model (get-mvp-model mvp)
          old-value (get-in old-model path)]

      (if (not= old-value new-value)
        ;; update the version/root model
        (reset! (:model mvp) [(increment-version mvp) (assoc-in old-model path new-value)])))))

(defn version
  "Gets the current version of the model"
  [mvp]
  (if (sequential? mvp)
    (recur (first mvp))
    (first @(:model mvp))))

(defn- add-subscription-record
  "Creates a subscription record and appends it to the specified collection."
  [mvp path func collection]
  (let [current-value (get-value mvp path)
        subscription-record [func path (atom current-value)]]
    (reset! collection (conj @collection subscription-record))
    (func { :old-value nil :new-value current-value :new-version (version mvp)})
    current-value))

(defn add-writer
  "Registers a subscriber that potentially causes model update."
  [mvp path subscriber-fn]
  (if (sequential? mvp)
    (recur (first mvp) (into (second mvp) path) subscriber-fn)
    (add-subscription-record mvp path subscriber-fn (:writers mvp))))

(defn add-reader
  "Registers a read-only subscriber."
  [mvp path subscriber-fn]
  (if (sequential? mvp)
    (recur (first mvp) (into (second mvp) path) subscriber-fn)
    (add-subscription-record mvp path subscriber-fn (:readers mvp))))

(defn- trigger-subscribers
  "Scans for and invokes subscribers which registered model value no longer matches the actual model value.
   Stops the scan and returns false if the model version changes in the process.
   Answers true if complete scan was successfully performed and the model version is the same."
  [mvp subscription]
  (let [version (first @(:model mvp))]
    (iterate-while
     (fn [[func path old-value]]
       (let [current-value (get-value mvp path)]
         (if (not= current-value @old-value)
           (do
             (func { :old-value old-value :new-value current-value :new-version version })
             (reset! old-value current-value)))))
     subscription
     #(= version (first @(:model mvp))))))

(defn delay-events
  "Suppresses subscription calls until the specified function has completed.
   Triggers subscribers if the model's version got changed."
  [mvp func]
  (let [mvp-object (if (sequential? mvp) (first mvp) mvp)
        scan-signal (:scanning mvp-object)
        scan-in-progress @scan-signal
        old-version (first @(:model mvp-object))]

    (if (not scan-in-progress)
      (reset! scan-signal true))

    (let [result-value (func)]
      (if (not scan-in-progress)
        (do
          ;; call subscribers if any change occurred
          (if (not= old-version (first @(:model mvp-object)))
            
            ;; TODO: detect infinite (>200) loops, print out last 20 paths
            (while (not
                    (and 
                     (trigger-subscribers mvp-object @(:writers mvp-object))
                     (trigger-subscribers mvp-object @(:readers mvp-object))))))
          (reset! scan-signal false)))
      result-value)))

(defn- if-subpaths-match
  "Answers true if the subpath fully matches first elements on the path."
  [subpath path]
  (let [subpath-length (count subpath)
        path-length (count path)]
    (if (> subpath-length path-length)
      false ;; path is more generic than path
      (loop [index 0]
        (cond
         (>= index subpath-length) true
         (not= (nth subpath index) (nth path index)) false
         :else (recur (inc index))))
      )))

(defn clear
  "Removes subscribers which path starts with the specified indices."
  [mvp path]
  (if (sequential? mvp)
    (recur (first mvp) (into (second mvp) path))
    (let [predicate #(not (if-subpaths-match path (nth % 1)))]
      (reset! (:writers mvp) (filter predicate @(:writers mvp)))
      (reset! (:readers mvp) (filter predicate @(:readers mvp))))))

(defn- resolve-mvp-ref
  "Resolves the specified mvp reference into mvp model.
   passes the parameters intact if it's not an mvp reference"
  [mvp path]
  (if (sequential? mvp)
    [(first mvp) (into (second mvp) path)]
    [mvp path]))

(defn update-value
  "Updates the value in the nested MVP model, similar to core/update-in."
  [mvp path func & args]
  (let [[mvp-object full-path] (resolve-mvp-ref mvp path)
        old-value (get-value mvp-object fullpath)
        new-value (apply func old-value args)]
    (delay-events mvp #(set-value-basic mvp-object full-path new-value))
    new-value))

(defn assoc-value
  "Associates the value in the nested MVP model, similar to core/assoc-in."
  [mvp path new-value]
  (if (sequential? mvp)
    (recur (first mvp) (into (second mvp) path) new-value)
    (delay-events mvp #(set-value-basic mvp path new-value))))
