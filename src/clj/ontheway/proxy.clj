(ns ontheway.proxy
  (:use [compojure.core]
        [ring.util.response :only [header response file-response]])
  (:require [clojure.data.json :as json]
            [clj-http.client :as http]
            [compojure.route :as route]
            [ontheway.config :as config]
            [ontheway.mapquest :as mapquest]))

(defn add-original-headers [response headers]
  (if (nil? headers)
    response
    (let [item (first headers)]
      (recur
        (header response (key item) (val item))
        (next headers)))))

(defn add-cors-headers [response request-headers]
  (->
   response
   (header "Content-Type" "application/json")
   (header "Access-Control-Allow-Credentials" "true")
   (header "Access-Control-Allow-Origin" config/hostname)))
