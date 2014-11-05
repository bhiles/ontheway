(ns ontheway.util
  (:use [clojure.string :only [trim join]])
  (:import [java.net URLEncoder]))

(defn url-encode [s]
  (URLEncoder/encode s "UTF-8"))

(defn mk-uri [base-uri query-params]
  (str base-uri "?"
       (join "&"
             (map (fn [[k v]] (str k "=" (url-encode v))) query-params))))
