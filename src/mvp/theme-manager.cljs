;   Copyright (c) Dmitry Belikov. All rights reserved.
(ns fc.theme-manager
  (:require [fc.mvp :as mvp]
            [fc.communication :as com]
            [fc.util :as u]
            [fc.mvp-helper :as h]
            [clojure.string :as cls]
            [goog.history.Html5History :as goog-history]))

;;
;; Theme manager maintains the theme that governs current screen synchronized with the page URL.
;; Page URL change automatically triggers theme transition.
;;
;; URL is parsed as http://.../some-page/theme/parameter1/parameter2/...
;; some-page is served from some-page.html by the server.
;;
;; Transition invocation sequence:
;; theme-new :prepare
;; theme-old :hide
;; theme-new :show
;; theme-old :stop
;; theme-new :start
;;

(def current-theme (atom {:name nil :theme nil :parameters nil}))

(def current-themes (atom nil))

(defn transit
  "Triggers a sequence of events to make a transition between the two specified themes"
  [new old]

  (let [new-pars (:parameters new)
        old-pars (:parameters old)]

    (if-let [prepare (get-in new [:theme :prepare])]
      (prepare new-pars))
    (if-let [hide (get-in old [:theme :hide])]
      (hide old-pars))
    (if-let [show (get-in new [:theme :show])]
      (show new-pars))
    (if-let [stop (get-in old [:theme :stop])]
      (stop old-pars))
    (if-let [start (get-in new [:theme :start])]
      (start new-pars))))

(defn transit-to-token
  "Triggers transition to the specified token (as returned by goog.history)"
  [string-token]

  ;;(u/l-v "Transition to token" string-token)

  (let [[content theme & pars] (cls/split string-token #"/")
        theme-name (or theme (get @current-themes "default"))
        new-theme {:name theme-name :theme (get @current-themes theme-name) :parameters pars}]

    ;;(u/l-v "theme-name" theme-name)
    ;;(u/l-v "New theme" new-theme)
    ;;(u/l-v "old name" (:name @current-theme))
    ;;(u/l-v "equality" (not= theme-name (:name @current-theme)))

    (if (and (:theme new-theme) (not= theme-name (:name @current-theme)))
      (do
        ;;(u/l "SWITCH TRIGGERED")
        (transit new-theme @current-theme)
        (reset! current-theme new-theme)))))

(def instantiate-history
  (let [history (goog.history.Html5History.)]
    (.setUseFragment history false)
    (.addEventListener history goog.history.EventType.NAVIGATE #(transit-to-token (.-token %)))
    (.setEnabled history true)
    history))

(def history (atom nil))

(defn set-themes!
  "Specifies the map of themes.
   Automatically initiates transition to the current theme based on the current browser's URL."
  [theme-map]
  (reset! current-themes theme-map)
  (if-not @history
    (reset! history instantiate-history))
  (transit-to-token (.getToken @history)))

(defn set-theme!
  "Sets current browser URL to initiate a transition to the specified theme."
  [theme & parameters]

  (let [[content] (cls/split (.getToken @history) #"/")
        new-token (apply str (interpose "/" (into [content theme] parameters)))]

    ;;(u/l-v "switching to theme" new-token)
    (.setToken @history new-token)))
