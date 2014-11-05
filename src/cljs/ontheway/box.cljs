(ns ontheway.box)

(defn find-box-corners [{:keys [start-lat start-lng end-lat end-lng]}]
  (let [extra 0.001
        sw-lat (- (min start-lat end-lat) extra)
        ne-lat (+ (max start-lat end-lat) extra)
        sw-lng (- (min start-lng end-lng) extra)
        ne-lng (+ (max start-lng end-lng) extra)]
    {:sw-lat sw-lat
     :sw-lng sw-lng
     :ne-lat ne-lat
     :ne-lng ne-lng}))

(defn max-box-corners [steps]
  (let [extra 0.005 ;; extra leaves room for the top of the page (and bottom)
        lats (mapcat (juxt :start-lat :end-lat) steps)
        lngs (mapcat (juxt :start-lng :end-lng) steps)
        sw-lat (- (apply min lats) extra)
        ne-lat (+ (apply max lats) extra)
        sw-lng (- (apply min lngs) extra)
        ne-lng (+ (apply max lngs) extra)]
    {:sw-lat sw-lat
     :sw-lng sw-lng
     :ne-lat ne-lat
     :ne-lng ne-lng}))

(defn within-box? [lat lng box]
  (let [{:keys [sw-lat sw-lng ne-lat ne-lng]} box]
    (and (< sw-lat lat ne-lat)
         (< sw-lng lng ne-lng))))

