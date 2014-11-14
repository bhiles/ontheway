(ns ontheway.core
  (:use-macros [dommy.macros :only [deftemplate sel1]])
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async :refer [<!]]
            [goog.dom :as dom]
            [blade :refer [L]]
            [dommy.core :as dommy]
            [ontheway.util :as u]
            [ontheway.api :as api]
            [ontheway.google :as google]
            [ontheway.yelp :as yelp]))

;; Setup

;; bootstrap to setup leaflet/clojurescript lib, blade
(blade/bootstrap)

;; set the more options collapser to not toggle
(.collapse (js/$ ".collapse") {:toggle false})

;; allows handling Nodelist like a seq
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

(defn to-query []
  (if-let [node (.getPlace autocompleteTo)]
    (.-formatted_address node)
    (.-value (dom/getElement "directions-to"))))

(defn category-query []
  (let [category (.-value (dom/getElement "directions-category"))]
    (if (u/empty-string? category)
      "food"
      category)))

(defn transportation-query []
  (.-value (dom/getElement "directions-transportation")))

(defn start-spinner []
  (->> "btn-go"
       dom/getElement
       (.create js/Ladda)
       .start))

(defn stop-spinner []
  (.stopAll js/Ladda))

;; HTML templates

(defn section-id [num]
  (str "biz-" num))

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
         [:a {:href (:url biz)}
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
           [:td (:review_count biz)]]
          [:tr
           [:td {:class "text-right"}]
           [:td
            [:small
             [:a
              {:href (google/maps-url start-point
                                      (-> biz
                                          :location
                                          :coordinate
                                          ((juxt :latitude :longitude)))
                                      end-point)}
              "Directions"]]]]]]]]]]))

(deftemplate no-biz-template []
  [:div {:id "no-biz-text" :class "row"}
   [:h3 "No businesses found"]])

;; Expand/Collapse HTML

(defn expand-biz-sidebar []
  (.setAttribute (dom/getElement "map-container")
                 "class" "col-md-8 no-right-padding")
  (.setAttribute (dom/getElement "biz-container")
                 "class" "col-md-4"))

(defn hide-biz-sidebar []
  (.setAttribute (dom/getElement "map-container")
                 "class" "col-md-12")
  (.setAttribute (dom/getElement "biz-container")
                 "class" ""))

(defn clear-biz-sidebar []
  (let [sidebar (dom/getElement "biz-container")]
    (dom/removeChildren sidebar)))

(defn hide-more-options []
  (.collapse (js/$ ".collapse") "hide"))

;; Map configuration/alteration

(def tile-url (str "http://server.arcgisonline.com"
                   "/ArcGIS/rest/services/Canvas"
                   "/World_Light_Gray_Base/MapServer/tile"
                   "/{z}/{y}/{x}"))

(defn setup-map [m lat lng]
  (-> m (.setView [lat lng] 12))
  (-> L (.tileLayer tile-url {:maxZoom 16 ;; this is the limitation of the tile
                              :id "examples.map-i875mjb7"})
      (.addTo m)))

(defn reset-map [m map-bounds]
  (.fitBounds m
              [[(:sw-lat map-bounds) (:sw-lng map-bounds)]
               [(:ne-lat map-bounds) (:ne-lng map-bounds)]]))

(defn add-numbered-marker [layer layer-items lat lng num]
  (let [NumberedDivIcon (.-NumberedDivIcon js/L)
        marker (-> L (.marker [lat, lng]
                              {:icon (NumberedDivIcon. {:number (str num)})})
                   (.on "click" #(aset js/window "location"
                                       (str "#" (section-id num)))))
        new-layer (.addLayer layer marker)]
    (swap! layer-items assoc num new-layer)))

(defn add-numbered-marker-active [layer layer-items lat lng num]
  (let [NumberedDivIconActive (.-NumberedDivIconActive js/L)
        marker (-> L (.marker [lat, lng]
                              {:icon (NumberedDivIconActive. {:number (str num)})})
                   (.on "click" #(aset js/window "location"
                                       (str "#" (section-id num)))))
        new-layer (.addLayer layer marker)]
    (swap! layer-items assoc num new-layer)))

(defn remove-marker [layer layer-items num]
  (let [marker (get @layer-items num)]
    (swap! layer-items dissoc num)
    (.removeLayer layer marker)))

;; Draw directions/markers

(defn draw-directions [m directions directions-layer]
  (let [{:keys [start-point end-point lines map-bounds]} directions
        start-circle (-> L (.circle (vec start-point) 8 {:color "green"}))
        end-circle (-> L (.circle (vec end-point) 8 {:color "red"}))
        lines (-> L (.polyline lines))]
    (doseq [l [start-circle end-circle lines]]
      (.addLayer directions-layer l))
    (.addTo directions-layer m)))

(defn draw-yelp-markers [m numbered-biz layer layer-items]
  (doseq [biz numbered-biz]
    (let [{:keys [id name url]} biz
          {:keys [latitude longitude]} (-> biz :location :coordinate)]
      (add-numbered-marker layer layer-items latitude longitude id)))
  (.addTo layer m))

;; Yelp sidebar

(defn create-biz-sidebar [start-point end-point numbered-biz layer layer-items]
  (dommy/append! (sel1 :#biz-container)
                 (biz-template start-point end-point numbered-biz))
  (doseq [biz numbered-biz]
    (let [{:keys [id name url]} biz
          {:keys [latitude longitude]} (-> biz :location :coordinate)
          mouseover (u/listen (dom/getElement
                               (section-id (:id biz))) "mouseover")
          mouseout (u/listen (dom/getElement
                              (section-id (:id biz))) "mouseout")]
      (go
       (while true
         (<! mouseover)
         (remove-marker layer layer-items id)
         (add-numbered-marker-active layer layer-items latitude longitude id)))
      (go
       (while true
         (<! mouseout)
         (remove-marker layer layer-items id)
         (add-numbered-marker layer layer-items latitude longitude id))))))

;; Yelp/Directions setup

(defn fetch-draw-directions [m to from transport-type directions-layer]
  (go
   (let [{:keys [lat-lngs start-point end-point map-bounds] :as directions}
         (<! (api/mapquest-directions to from transport-type))]
     (draw-directions m directions directions-layer)
     (reset-map m map-bounds)
     directions)))

(defn draw-yelp-info [m directions category layer layer-items]
  (go
   (let [{:keys [lat-lngs start-point end-point map-bounds]} directions
         businesses (<! (api/yelp-bounds map-bounds category))
         numbered-biz (yelp/filter-and-rank-businesses businesses lat-lngs)]
     (if (empty? numbered-biz)
       (dommy/append! (sel1 :#map-text-container) (no-biz-template))
       (do
         (draw-yelp-markers m numbered-biz layer layer-items)
         (create-biz-sidebar start-point end-point numbered-biz layer layer-items)
         (expand-biz-sidebar)) ;; reduce map size to allow for biz sidebar
       (reset-map m map-bounds)))))

;; Full page rendering after button submit

(defn setup-button-click [lat lng map]
  (let [clicks (u/listen (dom/getElement "btn-go") "click")
        directions-layer (-> L (.layerGroup []))
        biz-layer (-> L (.layerGroup []))
        biz-layer-items (atom {})]
    (go (while true
          ;; wait for a click
          (<! clicks)
                                        ; start loading spinner
          (start-spinner)
          ;; clear existing map's directions
          (.clearLayers directions-layer)
          ;; clear existing business markers
          (.clearLayers biz-layer)
          ;; clear the sidebar
          (hide-biz-sidebar)
          (clear-biz-sidebar)
          ;; hide the more options expander
          (hide-more-options)
          ;; remove no businesses found text (if even there)
          (u/remove-node "no-biz-text")
          ;; clear text (if not already cleared)
          (u/remove-node "explanation")
          ;; form params, directions, and yelp info
          (let [from (from-query lat lng)
                to (to-query)
                transport-type (transportation-query)
                category (category-query)
                directions (<! (fetch-draw-directions map to from
                                                      transport-type
                                                      directions-layer))]
            (<! (draw-yelp-info map directions category
                                biz-layer biz-layer-items))
            ;; stop loading spinner
            (stop-spinner)
            )))))

;; Main method

(go 
 (let [coords    (.-coords (<! (u/get-position)))
       latitude  (.-latitude coords)
       longitude (.-longitude coords)
       map (-> L (.map "mappy"))]
   (setup-map map latitude longitude)
   (setup-button-click latitude longitude map)))
