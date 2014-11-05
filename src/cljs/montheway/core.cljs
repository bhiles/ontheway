(ns montheway.core
  (:use [clojure.string :only [split]])
  (:use-macros [dommy.macros :only [deftemplate sel1 sel]])
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs-http.client :as http]
            [cljs.core.async :refer [put! chan <!]]
            [goog.dom :as dom]
            [goog.events :as events]
            [dommy.core :as dommy]
            [ontheway.config :as config]
            [ontheway.util :as u]
            [ontheway.google :as google]
            [ontheway.mapquest :as mapquest]
            [ontheway.yelp :as yelp]
            [montheway.waze :as waze]))

;; Setup

(extend-type js/NodeList
  ISeqable
  (-seq [array] (array-seq array 0)))

;; Form fields

(def autocompleteFrom (google.maps.places.Autocomplete.
                       (dom/getElement "directions-from")))
(def autocompleteTo (google.maps.places.Autocomplete.
                       (dom/getElement "directions-to")))

(defn from-query [lat lng]
  (if-let [node (.getPlace autocompleteFrom)]
    (.-formatted_address node)
    (let [form-val (.-value (dom/getElement "directions-from"))]
      (if (u/empty-string? form-val)
        (str lat "," lng)
        form-val))))

(defn from-query-current-location? []
  (u/empty-string? (.-value (dom/getElement "directions-from"))))

(defn to-query []
  (if-let [node (.getPlace autocompleteTo)]
    (.-formatted_address node)
    (.-value (dom/getElement "directions-to"))))

(defn start-spinner []
  (->> "mobile-btn-go"
       dom/getElement
       (.create js/Ladda)
       .start))

(defn stop-spinner []
  (.stopAll js/Ladda))

;; HTML templates

(defn section-id [num]
  (str "biz-" num))

(defn waze-maps-url-template [dest-text dest]
  (if (from-query-current-location?)
    [:p
     [:small
      [:a
       {:href (waze/mobile-maps-url dest )}
       (str "Waze directions to " dest-text)]]]))

(deftemplate biz-template [start-point end-point businesses]
  (for [biz businesses]
    [:div {:class "row"}
     [:section {:id (section-id (:id biz))}
      [:div {:class "media"}
       [:a {:class "pull-left"}
        [:img
         {:class "media-object"
          :style "width: 180px; height: auto; overflow: hidden;"
          :src (:image_url biz)}]]
       [:div
        {:class "media-body"}
        [:h4
         [:a {:href (str "yelp:///biz/"
                         (last (split (:url biz) #"/")))}
          (str (:id biz) ". " (:name biz))]]
        [:table {:class "table table-condensed"}
         [:tbody
          [:tr
           [:td {:class "text-right"}
            "Categories"]
           [:td (->> (:categories biz)
                     (mapcat
                      (fn [c]
                        [(first c) [:br]])))]]
          [:tr
           [:td {:class "text-right"}
            "Rating"]
           [:td (:rating biz)]]
          [:tr
           [:td {:class "text-right"}
            "Reviews"]
           [:td (:review_count biz)]]]]
         [:p
          [:small
           [:a
            {:href (google/mobile-maps-url start-point
                                    (-> biz
                                        :location
                                        :coordinate
                                        (select-keys [:latitude :longitude])
                                        vals))}
            "Google directions to way-point"]]]
        [:p
          [:small
           [:a
            {:href (google/mobile-maps-url (-> biz
                                               :location
                                               :coordinate
                                               (select-keys [:latitude :longitude])
                                               vals)
                                           end-point)}
            "Google directions to destination"]]]
        (waze-maps-url-template "way-point" (-> biz
                                                :location
                                                :coordinate
                                                (select-keys [:latitude :longitude])
                                                vals))
        (waze-maps-url-template "destination" end-point)]]]]))

;; Display Yelp businesses

(defn display-businesses [to from]
  (go
   (let [{:keys [lat-lngs start-point end-point map-bounds] :as directions}
             (<! (mapquest/directions to from "driving"))
         numbered-biz (<! (yelp/find-and-rank-businesses
                           map-bounds lat-lngs "food"))]
     (dommy/append! (sel1 :#biz-container)
                    (biz-template start-point end-point numbered-biz)))))

;; Page rendering logic after button submit

(defn setup-button-click [lat lng]
  (let [clicks (u/listen (dom/getElement "mobile-btn-go") "click")]
    (go (while true
          (<! clicks) ;; wait for a click
          (start-spinner)
          (<! (display-businesses (from-query lat lng) (to-query)))
          (stop-spinner)))))

;; Main method

(go 
 (let [coords    (.-coords (<! (u/get-position)))
       latitude  (.-latitude coords)
       longitude (.-longitude coords)]
   (setup-button-click latitude longitude)))
