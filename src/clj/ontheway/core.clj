(ns ontheway.core
  (:use [compojure.core]
        [ring.util.response :only [header file-response]])
  (:require [clojure.data.json :as json]
            [clj-http.client :as http]
            [compojure.handler :as handler]
            [compojure.route :as route]
            [ontheway.config :as config]
            [ontheway.mapquest :as mapquest]
            [ontheway.yelp :as yelp]))

;; Utils

(defn json-response [data & [status]]
  {:status (or status 200)
   :headers {"Content-Type" "application/json"
             "Access-Control-Allow-Origin" config/hostname
             "Access-Control-Allow-Credentials" "true"}
   :body (json/write-str data)})

;; /find-biz endpoint

(defn find-businesses [to from transport-type category]
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
  (GET "/" [] (file-response "index.html" {:root "resources/public"}))
  
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
