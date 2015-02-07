(ns moot
  (:require [goog.events :as goog.events]))

(defn log
  "Log msg on the console."
  [msg]
  (.log js/console (pr-str msg)))

(def state
  "The state of the client."
  (atom
   {:you  {:id 105 :title "Mr Pink"      :color :pink
           :position {:lat 42.365257 :lng -71.087246}}
    :all #{{:id 101 :title "Mr Blue"      :color :blue
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
            :position {:lat 42.366465 :lng -71.095194}}}
    :markers {}
    :the-map nil}))

(defn my-name-is!
  "Update your name everywhere it matters."
  [name]
  (let [id (get-in @state [:you :id])]
    (log [:my-name-is! name :id id])
    (swap! state (fn [state]
                   (let [state (update-in state [:you :title] (constantly name))]
                     (update-in state [:all] conj (:you state)))))
    (.setTitle (get-in @state [:markers id]) name)))

(defn marker-visible?
  "True if marker for id is visible.  False otherwise."
  [id]
  (if-let [marker (get-in @state [:markers id])]
    (.getVisible marker)
    true))

(defn toggle-visible!
  "Toggle visibility of marker with id."
  [id]
  (if-let [marker (get-in @state [:markers id])]
    (.setVisible marker (not (.getVisible marker)))))

(def marker-icon-colors
  "The colors available for map marker (x.png and x-dot.png) icons."
  (let [colors [:blue :green :red :yellow :orange :pink :purple]
        dotted (map #(keyword (str (name %) "-dot")) colors)]
    (reduce conj colors dotted)))

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
                      (fn? v) v
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

(def body     (element-for-tag :body))
(def button   (element-for-tag :button))
(def div      (element-for-tag :div))
(def form     (element-for-tag :form))
(def h1       (element-for-tag :h1))
(def h2       (element-for-tag :h2))
(def head     (element-for-tag :head))
(def html     (element-for-tag :html))
(def img      (element-for-tag :img))
(def input    (element-for-tag :input))
(def label    (element-for-tag :label))
(def link     (element-for-tag :link))
(def p        (element-for-tag :p))
(def script   (element-for-tag :script))
(def span     (element-for-tag :span))
(def style    (element-for-tag :style))
(def textarea (element-for-tag :textarea))
(def title    (element-for-tag :title))

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
  "The icon with color."
  [color]
  (let [base "http://maps.google.com/mapfiles/ms/icons/"]
    (str base (name color) ".png")))

(defn goog-map-micon
  "The micon with color.  (Not sure what the difference is.)"
  [color]
  (let [base "http://maps.google.com/mapfiles/ms/micons/"]
    (str base (name color) ".png")))

(defn animate-marker!
  "Animate marker with id according to how for 2 seconds."
  [id & how]
  (let [how (first how)]
    (if how (.setTimeout js/window #(animate-marker! id) 2100))
    (if-let [marker (get-in @state [:markers id])]
      (.setAnimation
       marker
       (get {:bounce google.maps.Animation.BOUNCE
             :drop   google.maps.Animation.DROP} how how)))))

(defn mark-guy
  "Drop a marker for guy on map with optional animation."
  [map guy & animate]
  (letfn [(make [m]
            (google.maps.Marker.
             (clj->js (assoc guy
                             :map map
                             :icon (goog-map-micon (:color guy))
                             :visible (if m (.getVisible m) true)))))]
    (swap! state #(update-in % [:markers (:id guy)] make))
    (animate-marker! (:id guy) :drop)))

(defn new-map-guys
  "A new map showing all the guys."
  [guys]
  (let [bound (new-goog-bound (map (comp new-goog-latlng :position) guys))
        bottom-right google.maps.ControlPosition.BOTTOM_RIGHT
        options {:center (.getCenter bound)
                 :zoom 8
                 :panControlOptions {:position bottom-right}
                 :zoomControlOptions {:position bottom-right}}
        result (element-by-id :the-map :div)
        the-map (new-goog-map result options)]
    (.fitBounds the-map bound)
    (doseq [guy guys] (mark-guy the-map guy :drop))
    (swap! state #(update-in % [:the-map] (constantly the-map)))
    result))

(defn show-all-guys!
  []
  (let [state @state]
    (.fitBounds (:the-map state)
                (new-goog-bound
                 (map (comp new-goog-latlng :position)
                      (:all state))))))

(defn goog-icon-img-for
  "The icon image for guy."
  [guy]
  (doto (img {:class :legend-icon
              :src (goog-map-icon (:color guy))
              :alt (:title guy)})
    (goog.events/listen "click" #(animate-marker! (:id guy) :bounce))))

(defn legend-for-guy
  "The legend entry for guy."
  [guy]
  (let [id (:id guy)
        title (:title guy)
        you? (= (get-in @state [:you :id]) id)]
    (div {:id title :class :guy}
         (span {}
               (doto (input {:type :checkbox :checked (marker-visible? id)})
                 (goog.events/listen "click" #(toggle-visible! id)))
               (goog-icon-img-for guy)
               (if you?
                 (let [text (input {:type :text :value title})]
                   (doto text
                     (goog.events/listen "keyup" #(my-name-is! (.-value text)))))
                 title)))))

(defn show-legend
  "Show the legend if show?.  Otherwise hide it."
  [show?]
  (let [legend (element-by-id :legend)
        you (element-by-id :you)]
    (log [:show-legend {:legend legend :show? show?}])
    (set-attributes you {:class (if show? "legend" "legend show")})
    (set-attributes legend {:class (if show? "legend show" "legend")})))

(defn render-you
  [state]
  (let [you (:you state)
        title (:title you)
        visible? (marker-visible? (:id you))]
    (doto (div {:id :you :class "legend show"}
               (div {:id title :class :guy}
                    (span {}
                          (input {:type :checkbox
                                  :disabled true
                                  :checked visible?})
                          (goog-icon-img-for you)
                          title)))
      (goog.events/listen "click" #(show-legend true)))))

(defn render-legend
  "Render the legend according to state."
  [state]
  (let [you (:you state)
        guys (cons you (remove #(= (:id you) (:id %)) (:all state)))
        buttons
        (div {:class :buttons}
             (span {:class :guy :id :close :align :left}
                   (doto (button {:type :button} "Where is everyone?")
                     (goog.events/listen "click" show-all-guys!)))
             (span {:class :guy :id :close :align :right}
                   (doto (button {:type :button} "Close")
                     (goog.events/listen "click" #(show-legend false)))))]
    (apply (partial div {:id :legend :class "legend"})
           (concat (for [guy guys] (legend-for-guy guy))
                   (list buttons)))))

(defn page
  "Render the page HTML."
  [state]
  (html {}
        (head {}
              (link {:type :text/css :rel :stylesheet :href "./moot.css"})
              (script {:type "text/javascript" :src "out/goog/base.js"})
              (title {} "Where is everyone?")
              (style {}))
        (body {}
              (render-legend state)
              (render-you state)
              (new-map-guys (:all state)))))

(goog.events/listen js/window "load" #(page @state))
