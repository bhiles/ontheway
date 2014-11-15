(ns ontheway.core
  (:use [compojure.core]
        [ring.util.response :only [header resource-response]])
  (:require [clojure.data.json :as json]
            [clj-http.client :as http]
            [compojure.handler :as handler]
            [compojure.route :as route]
            [environ.core :refer [env]]
            [ontheway.mapquest :as mapquest]
            [ontheway.yelp :as yelp]))

;; Utils

(defn json-response [data & [status]]
  {:status (or status 200)
   :headers {"Content-Type" "application/json"
             "Access-Control-Allow-Origin" (env :hostname)
             "Access-Control-Allow-Credentials" "true"}
   :body (json/write-str data)})

;; /find-biz endpoint

(defn find-businesses
  "This endpoints is specifically designed for the mobile client.
  It combines the /mapquest-directions endpoint and the
  /yelp-bounds endpoints into one call to allow the server to do the
  computationally heavy lifting instead of the mobile client."
  [to from transport-type category]
  (let [{:keys [lat-lngs start-point end-point map-bounds]}
            (mapquest/directions to from transport-type)
        numbered-biz (yelp/find-and-rank-businesses map-bounds
                                                    lat-lngs
                                                    category)]
    {:start-point start-point
     :end-point end-point
     :businesses numbered-biz}))

(defroutes app-routes
  
  ;; serves html
  (GET "/" [] (resource-response "index.html" {:root "public"}))
  
  ;; API endpoints 
  (GET "/yelp-bounds" {params :params}
       (json-response
        (yelp/fetch-businesses-bounds (:bounds params)
                                      (:term params))))
  (GET "/mapquest-directions" {params :params}
       (json-response
        (mapquest/directions (:to params)
                             (:from params)
                             (:transport-type params))))
  (GET "/find-biz" {params :params}
       (json-response (find-businesses (:to params)
                                       (:from params)
                                       (:transport params)
                                       (:term params))))

  ;; serves defaults
  (route/resources "/")
  (route/not-found "Page not found"))

(def handler
  (handler/site app-routes))
