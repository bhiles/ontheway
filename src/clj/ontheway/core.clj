(ns ontheway.core
  (:use [compojure.core]
        [ring.util.response :only [header response file-response]]
        [clojure.string :only [join]]
        [clojure.walk :only [keywordize-keys]])
  (:require [clojure.data.json :as json]
            [clj-http.client :as http]
            [compojure.handler :as handler]
            [compojure.route :as route]
            [ontheway.config :as config]
            [ontheway.mapquest :as mapquest]
            [ontheway.yelp :as yelp]
            [ontheway.proxy :as proxy])
  (:import [java.net URLEncoder]
           [java.io ByteArrayInputStream]))

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

;; /proxy endpoint - proxies requests

(defn proxy-request 
  "Attribution: https://github.com/rm-hull/programming-enchiladas"
  [req]
   (let [url (get-in req [:params :url])
         resp (http/get url {:as :byte-array})]
      (if (= (:status resp) 200)
        (->
          (response (ByteArrayInputStream. (:body resp)))
          (proxy/add-original-headers (:headers resp))
          (proxy/add-cors-headers (:headers req))
          (header "x-proxied-by" "On the way")))))

(defroutes app-routes
  
  ;; serves html
  (GET "/" [] (file-response "index.html" {:root "resources/public"}))
  
  ;; API endpoints 
  (GET "/yelp-bounds" {params :params}
       (json-response
        (yelp/fetch-businesses-bounds (:bounds params)
                                      (:term params))))
  (GET "/find-biz" {params :params}
       (json-response (find-businesses (:to params)
                                       (:from params)
                                       (:transport params)
                                       (:term params))))
  (GET "/proxy" [:as req] (proxy-request req))

  ;; serves defaults
  (route/resources "/")
  (route/not-found "Page not found"))

(def handler
  (handler/site app-routes))
