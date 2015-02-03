(ns moot
  (:require
   [goog.events :as goog.events]))

(def state
  "The state of the client."
  (atom
   {:you  {:title "Mr Pink"      :color :pink
           :position {:lat 42.365257 :lng -71.087246}}
    :all [{:title "Mr Blue"      :color :blue
           :position {:lat 42.357465 :lng -71.095194}}
          {:title "Mr Green"     :color :green
           :position {:lat 42.364251 :lng -71.110300}}
          {:title "Mr LightBlue" :color :lightblue
           :position {:lat 42.364876 :lng -71.102352}}
          {:title "Mr Orange"    :color :orange
           :position {:lat 42.369347 :lng -71.101107}}
          {:title "Mr Pink"      :color :pink
           :position {:lat 42.365257 :lng -71.087246}}
          {:title "Mr Purple"    :color :purple
           :position {:lat 42.361198 :lng -71.103983}}
          {:title "Mr Red"       :color :red
           :position {:lat 42.372083 :lng -71.082062}}
          {:title "Mr Yellow"    :color :yellow
           :position {:lat 42.366465 :lng -71.095194}}]}))

(def map-marker-icon-colors
  "The colors available for map marker icons."
  (let [colors [:blue :green :red :yellow :orange :pink :purple]
        dotify (fn [c] (keyword (str (name c) "-dot")))]
    (reduce conj colors (map dotify colors))))

(defn log
  "Log msg on the console."
  [msg]
  (.log js/console (pr-str msg)))

(defn append-child
  "Append HTML element child to parent."
  [parent child]
  (let [parent (get {:head (.-head js/document)
                     :body (.-body js/document)}
                    parent parent)]
    (log ["append-child" {:parent parent}])
    (log ["append-child" {:child child}])
    (.appendChild parent child)
    parent))

(defn set-attributes
  "Set attributes on element according to attribute-map."
  [element attribute-map]
  (doseq [[k v] attribute-map]
    (.setAttribute element (name k) (name v)))
  element)

(defn create-element
  "Create an element of kind tag with attributes map."
  [tag attributes]
  (let [element (.createElement js/document (name tag))]
    (set-attributes element attributes)))

(defn element-for-tag
  "An element function for tag that takes an attribute map and a
  sequence of child elements and renders them in HTMl."
  [tag]
  (fn [attributes & kids]
    (let [element (create-element tag attributes)]
      (doseq [kid kids]
        (cond (instance? js/Element kid) (append-child element kid)
              (fn? kid) (append-child element kid)
              :else (append-child element (.createTextNode js/document kid))))
      element)))

(def div  (element-for-tag :div))
(def h1   (element-for-tag :h1))
(def h2   (element-for-tag :h2))
(def img  (element-for-tag :img))
(def p    (element-for-tag :p))
(def span (element-for-tag :span))
(def text (element-for-tag :text))

(defn element-by-id
  "The element with id, adding tag with that id to body if necessary."
  ([id]
   (.getElementById js/document (name id)))
  ([id tag]
   (let [tag (if (keyword? tag) (element-for-tag tag) tag)]
     (if-let [result (element-by-id id)] result
             (let [result (tag {:id (name id)})]
               (append-child :body result)
               result)))))

(defn new-goog-latlng
  "A LatLng from lat and lng coordinates or position map."
  ([lat lng]
   (google.maps.LatLng. lat lng))
  ([position]
   (new-goog-latlng (:lat position) (:lng position))))

(defn new-goog-bound
  "The Google map bounds encompassing the seq of latlngs."
  [latlngs]
  (let [result (google.maps.LatLngBounds.)]
    (doseq [ll latlngs] (.extend result ll))
    result))

(defn new-goog-map
  "A new Google map on element with options."
  [element options]
  (google.maps.Map. element (clj->js options)))

(defn goog-map-icon
  [color]
  (let [base "http://maps.google.com/mapfiles/ms/icons/"]
    (str base (name color) ".png")))

(defn goog-map-micon
  [color]
  (let [base "http://maps.google.com/mapfiles/ms/micons/"]
    (str base (name color) ".png")))

(defn mark-guy
  "Drop a marker for guy on map with optional animation."
  [map guy & animate]
  (let [animation (get {:bounce google.maps.Animation.BOUNCE
                        :drop   google.maps.Animation.DROP}
                       (first animate))]
    (google.maps.Marker.
     (clj->js (assoc guy
                     :map map
                     :icon (goog-map-micon (:color guy))
                     :animation animation)))))

(defn map-guys
  "A new map showing all the guys."
  [guys]
  (let [bound (new-goog-bound (map (comp new-goog-latlng :position) guys))
        options {:center (.getCenter bound) :zoom 8}
        result (new-goog-map (element-by-id :map :div) options)]
    (.fitBounds result bound)
    (doseq [guy guys] (mark-guy result guy :drop))
    result))

(defn you-are
  [you]
  (span {:id :you}
        (img {:src (goog-map-icon (:color you)) :alt (:title you)})
        (:title you)))

(defn legend-guy
  [guy]
  (div {:id (:title guy)
        :class :guy}
       (span {}
             (img {:class :legend-icon
                   :src (goog-map-icon (:color guy))
                   :alt (:title guy)})
             (:title guy))))

(defn legend
  [state]
  (let [you (:you state)
        guys (conj (remove #(= you %) (:all state)) you)]
    (log guys)
    (apply (partial div {:class "legend show"})
           (for [guy guys] (legend-guy guy)))))

(defn show-page
  [state]
  (append-child :body
                (div {:class "legend show"}
                     (legend-guy (:you state))))
  (append-child :body
                (legend state))
  (map-guys (:all state)))

(goog.events/listen js/window "load" #(show-page @state))
