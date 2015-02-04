(ns moot
  (:require [goog.events :as goog.events]))

(def state
  "The state of the client."
  (atom
   {:you  {:id 105 :title "Mr Pink"      :color :pink
           :position {:lat 42.365257 :lng -71.087246}}
    :all [{:id 101 :title "Mr Blue"      :color :blue
           :position {:lat 42.357465 :lng -71.095194}}
          {:id 102 :title "Mr Green"     :color :green
           :position {:lat 42.364251 :lng -71.110300}}
          {:id 103 :title "Mr LightBlue" :color :lightblue
           :position {:lat 42.364876 :lng -71.102352}}
          {:id 104 :title "Mr Orange"    :color :orange
           :position {:lat 42.369347 :lng -71.101107}}
          {:id 105 :title "Mr Pink"      :color :pink
           :position {:lat 42.365257 :lng -71.087246}}
          {:id 106 :title "Mr Purple"    :color :purple
           :position {:lat 42.361198 :lng -71.103983}}
          {:id 107 :title "Mr Red"       :color :red
           :position {:lat 42.372083 :lng -71.082062}}
          {:id 108 :title "Mr Yellow"    :color :yellow
           :position {:lat 42.366465 :lng -71.095194}}]}))

(def markers
  "The markers on the map."
  (atom {}))

(def map-marker-icon-colors
  "The colors available for map marker (x.png and x-dot.png) icons."
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
    (.appendChild parent child)
    parent))

(defn set-attributes
  "Set attributes on element according to attribute-map."
  [element attribute-map]
  (doseq [[k v] attribute-map]
    (let [value (cond (true? v) "true"
                      (false? v) "false"
                      (number? v) (str v)
                      :else (name v))]
      (.setAttribute element (name k) value))))

(defn element-for-tag
  "An element function for tag that takes an attribute map and a
  sequence of child elements and renders them in HTMl."
  [tag]
  (let [make (get {:html #(.-documentElement %)
                   :head #(.-head %)
                   :body #(.-body %)} tag
                   #(.createElement % (name tag)))]
    (fn [attributes & kids]
      (let [element (make js/document)]
        (set-attributes element attributes)
        (doseq [kid kids]
          (cond (fn? kid) (append-child element kid)
                (instance? js/Element kid) (append-child element kid)
                :else (append-child element (.createTextNode js/document kid))))
        element))))

(def body  (element-for-tag :body))
(def div   (element-for-tag :div))
(def form  (element-for-tag :form))
(def h1    (element-for-tag :h1))
(def h2    (element-for-tag :h2))
(def head  (element-for-tag :head))
(def html  (element-for-tag :html))
(def img   (element-for-tag :img))
(def input (element-for-tag :input))
(def label (element-for-tag :label))
(def p     (element-for-tag :p))
(def span  (element-for-tag :span))
(def style (element-for-tag :style))
(def title (element-for-tag :title))

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
                     :animation animation
                     :visible true)))))

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

(defn goog-legend-icon-for
  [guy]
  (img {:class :legend-icon
        :src (goog-map-icon (:color guy))
        :alt (:title guy)}))

(defn legend-for-guy
  [guy]
  (div {:id (:title guy) :class :guy}
       (span {}
             (input {:type :checkbox :checked true})
             (goog-legend-icon-for guy)
             (:title guy))))

(defn legend
  [state]
  (let [you (:you state)
        guys (remove #(= (:id you) (:id %)) (:all state))]
    (log guys)
    (apply (partial div {:class "legend show"})
           (form {}
                 (div {:class :guy :id (:title you)}
                      (span {}
                            (input {:type :checkbox :checked true})
                            (goog-legend-icon-for you)
                            (input {:type :text :value (:title you)}))))
           (for [guy guys] (legend-for-guy guy)))))

(defn show-page
  [state]
  (html {}
        (head {}
              (title {} "Where is everyone?")
              (style {}))
        (body {}
              (div {:class "legend show"} (legend-for-guy (:you state)))
              (legend state))
        (map-guys (:all state))))

(goog.events/listen js/window "load" #(show-page @state))
