;   Copyright (c) Dmitry Belikov. All rights reserved.
(ns fc.timeline
  (:use [fc.util :only [l l-v]])
  (:use-macros [macroses :only [set-id c]])
  (:require [goog.structs.PriorityQueue :as goog-priority-queue]
            [goog.Timer :as goog-timer]
            [fc.mvp :as mvp]
            [fc.communication :as com]
            [fc.mvp-helper :as h]
            [fc.theme-manager :as man]))

;;
;; Implements simulation timeline.
;;
;; 'setup-rendering starts the timer and calls the specified callback to render canvas.
;; in-game events can be scheduled with 'schedule-event and they will be invoked from the same
;;    timer at the specified time moment
;; 'get-now returns the current time, use (+ (get-now) X) to schedule future event.
;;
;; Timer frequency is adjusted automatically to provide optimal rendering
;;       responsiveness and still leave enough CPU ticks for the device to function smoothly.
;; Formula: max(30, mean(5 last measurements) + 10ms)
;;

(def zero-point (.getTime (js/Date.)))
(def pq (goog.structs.PriorityQueue.))
(def timer (goog.Timer. 20))

;; 5 last measurements of the timer cycle
(def performance-history (atom '(20)))
(def rendering-function (atom (fn [])))
(def min-frequency-in-ms 30)
;;(def min-frequency-in-ms 1000)

(defn get-now
  "Answers the current time moment."
  []
  (- (.getTime (js/Date.)) zero-point))

(def last-fps-shown (atom (get-now)))
(def show-fps-in 5000)

(defn schedule-in
  "Invokes the specified callback once time is past the specified time moment.
   Time moment is (+ (get-now) X-in-ms). Callback is a function that receives no arguments."
  [delay-in-ms callback]
  (.enqueue pq (+ (get-now) delay-in-ms) callback))

(defn on-timer-tick-unsafe
  []

  ;; run logic and measure its running time
  (let [started (get-now)]
    ;; artificial delay for testing
    ;; (loop [i 100000000] (if (> i 0) (recur (dec i))))

    ;; process scheduled events
    (while (and (not (.isEmpty pq)) (< (.peekKey pq) started))
      ((.dequeue pq)))

    ;; render the scene
    (@rendering-function)

    ;; save running time of the current iteration
    (swap! performance-history #(take 5 (cons (- (get-now) started) %))))

  ;; adjust timer
  (let [mean (quot (apply + @performance-history) (count @performance-history))
        delay (max min-frequency-in-ms (+ 10 mean))]
    (if (> (- (get-now) @last-fps-shown) show-fps-in)
      (do
        (reset! last-fps-shown (get-now))
        (l-v "Rendering FPS" (/ 1000 delay))))
    (.setInterval timer delay)))

(defn on-timer-tick
  "Processes events and invokes the rendering function.
   Keeps track of performance and adjust the timer frequency as necessary."
  []

  (let [exception (js/swallowEverything #(on-timer-tick-unsafe))]
        (if exception
          (l-v "Exception in timeline:" exception))))

(defn stop-timeline
  "Stops the timer, resets the rendering function and release all scheduled events."
  []
  (.stop timer)
  (reset! rendering-function (fn []))
  (.clear pq))

(defn setup-rendering
  "Sets up the rendering function and starts tracking queued events on timer ticks.
   Automatically destroys previously queued events and resets the timeline."
  [render-callback]

  ;; cancel current state
  (stop-timeline)

  ;; sets up new rendering function
  (reset! rendering-function render-callback)
  (.start timer))

(.addEventListener timer goog.Timer.TICK on-timer-tick)

(comment
  (def timer (goog.Timer. 1000))
  (.addEventListener timer goog.Timer.TICK #(l-v "timer" (get-now)))

  (.start timer)
  (.setInterval timer 500)
  (.stop timer)
  
  (let [timer (goog.Timer. resolution)]
    (.addEventListener timer goog.Timer.TICK on-timer-tick)
    (.start timer))
  (schedule-in 20 #(l "Called in 20 ms"))
  (schedule-in 20000 #(l "Called in 20000 ms"))
  (setup-rendering #(l "render function call")))
