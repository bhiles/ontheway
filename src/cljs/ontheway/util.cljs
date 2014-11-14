(ns ontheway.util
  (:use [clojure.string :only [trim join]])
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async :refer [put! chan]]
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

(defn hostname
  "Returns the hostname of the current page.
   For example: http://example.com:8080"
  []
  (let [host  (-> js/window .-location .-host)
        protocol (-> js/window .-location .-protocol)]
    (str protocol "//" host)))

(defn url-encode [s]
  (js/encodeURIComponent s))

(defn mk-uri [base-uri query-params]
  (str base-uri "?"
       (join "&"
             (map (fn [[k v]] (str k "=" (url-encode v))) query-params))))

(defn json-parse [s]
  (js->clj
   (.parse js/JSON s)
   :keywordize-keys true))

;; GEO helpers

(defn get-position []
  (let [out (chan)
        geo (.-geolocation js/navigator)]
    (.getCurrentPosition geo (fn [pos] (put! out pos)))
    out))

(def pi (.-PI js/Math))

(defn sin [num]
  (.sin js/Math num))

(defn cos [num]
  (.cos js/Math num))

(defn asin [num]
  (.asin js/Math num))

(defn sqrt [num]
  (.sqrt js/Math num))

(defn to-radians [num]
  (/ (* pi num) 180))

(defn distance-between
  "The distance between the two points (in miles) using the Haversine formula."
  [[lat1 lng1] [lat2 lng2]]
  (let [R 3963.1676 ; earth's radius (in miles)
        dlat (to-radians (- lat2 lat1))
        dlng (to-radians (- lng2 lng1))
        lat1 (to-radians lat1)
        lat2 (to-radians lat2)
        a (+ (* (sin (/ dlat 2))
                (sin (/ dlat 2)))
             (* (sin (/ dlng 2))
                (sin (/ dlng 2))
                (cos lat1)
                (cos lat2)))]
    (* R 2 (asin (sqrt a)))))
