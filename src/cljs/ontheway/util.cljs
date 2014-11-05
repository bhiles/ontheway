(ns ontheway.util
  (:use [clojure.string :only [trim join]])
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [ontheway.config :as config]
            [cljs.core.async :refer [put! chan <!]]
            [goog.dom :as dom]
            [goog.events :as events]))

;; HTML helpers

(defn remove-node [id]
  (if-let [node (dom/getElement id)]
    (.remove node)))

(defn empty-string? [s]
  (-> s
      trim
      empty?))

(defn listen [el type]
  (let [out (chan)]
    (events/listen el type
      (fn [e] (put! out e)))
    out))

;; HTTP helpers

(defn url-encode [s]
  (js/encodeURIComponent s))

(defn mk-uri [base-uri query-params]
  (str base-uri "?"
       (join "&"
             (map (fn [[k v]] (str k "=" (url-encode v))) query-params))))

(defn proxy-url [url]
  (str config/hostname "/proxy?url=" (url-encode url)))

(defn json-parse [s]
  (js->clj
   (.parse js/JSON s)
   :keywordize-keys true))
