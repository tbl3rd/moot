(ns moot.client
  (:require [cljs.reader :as reader]
            [clojure.string :as s]
            [goog.dom :as goog.dom]
            [goog.events :as goog.events]
            [goog.net.EventType :as goog.net.EventType]
            [goog.net.XhrIo :as goog.net.XhrIo]))

(defn log
  "Log msg on the console."
  [msg]
  (.log js/console (pr-str msg)))

(def state
  "The state of the client."
  (atom {:you {:id 105 :title "Mr Pink"      :color :pink
               :position {:lat 42.365257 :lng -71.087246}}
         :all {101 {:id 101 :title "Mr Blue"      :color :blue
                    :position {:lat 42.357465 :lng -71.095194}}
               102 {:id 102 :title "Mr Green"     :color :green
                    :position {:lat 42.364251 :lng -71.110300}}
               103 {:id 103 :title "Mr LightBlue" :color :lightblue
                    :position {:lat 42.364876 :lng -71.102352}}
               104 {:id 104 :title "Mr Orange"    :color :orange
                    :position {:lat 42.369347 :lng -71.101107}}
               105 {:id 105 :title "Mr Pink"      :color :pink
                    :position {:lat 42.365257 :lng -71.087246}}
               106 {:id 106 :title "Mr Purple"    :color :purple
                    :position {:lat 42.361198 :lng -71.103983}}
               107 {:id 107 :title "Mr Red"       :color :red
                    :position {:lat 42.372083 :lng -71.082062}}
               108 {:id 108 :title "Mr Yellow"    :color :yellow
                    :position {:lat 42.366465 :lng -71.095194}}}
         :markers {}
         :map-id 901
         :the-map nil}))

(defn my-name-is!
  "Update your name everywhere it matters."
  [name]
  (let [id (get-in @state [:you :id])
        state (swap! state
                     (fn [state]
                       (-> state
                           (assoc-in [:you :title] name)
                           (assoc-in [:all id :title] name))))]
    (.setItem js/localStorage (str (:map-id state)) name)
    (when-let [mark (get-in state [:markers id])]
      (.setTitle mark name))))

(defn marker-visible?
  "True if marker for id is visible.  False otherwise."
  [id]
  (if-let [marker (get-in @state [:markers id])]
    (.getVisible marker)
    true))

(defn set-visible!
  "Set visibility of marker with id to visible?."
  [id visible?]
  (when-let [marker (get-in @state [:markers id])]
    (.setVisible marker visible?)))

(def marker-icon-colors
  "The colors available for map marker (x.png and x-dot.png) icons."
  (let [colors [:blue :green :red :yellow :orange :pink :purple]
        dotted (map #(keyword (str (name %) "-dot")) colors)]
    (reduce conj colors dotted)))

(defn css
  "A CSS style rule in a string specified in EDN for STYLE elements.
  WARNING: This function knows nothing about CSS.  GIGO.

  > (css #{:body :html} (let [all {:% 100}] {:height all :width all}))
  ;=> body, html { height:100%; width:100% }
  > (css [:ul :li] {:color :red :padding {:px 5})
  ;=> ul li { color:red; padding:5px }
  > (css :div {[:duration :-o- :-ms-] {:s 1}})
  ;=> div { duration:1s; -o-duration:1s; -ms-duration:1s; }

  Use a keyword or string SELECT to name a tag class or id.
  Use a string SELECT for pseudo classes and media queries.
  A set SELECT is an unordered multiple selector, whereas a vector is
  an ordered descendant selector.
  A function SELECT (constantly ...) is a general escape to the above
  interpretation.  CSS just calls it and uses its value as a selector.

  PROPS specifies the properties, usually as a map with keywords for
  the property names.  Setting values are a work in progress ...
  Keyword, string, and number settings evaluate as ... expected.
  Use a map setting for measures where the key is the units and the
  value is the quantity: {:s 1} ;=> 1s
  Use a vector property to prefix the first element with the rest
  elements to generate vendor-prefixed properties.
  Again use a function (constantly ...) to escape interpretation.

  TODO: url(), vendor prefixes ... and so on.  Yes, this is a hack."
  [select props]
  (letfn [(units [m] (let [[k v] (first m)] (str v (name k))))
          (namer [x] (cond (keyword? x) (name x)
                           (string? x) x
                           (number? x) x
                           (map? x) (units x)
                           (fn? x) (x)))
          (joiner [punct] (fn [props] (s/join punct (map namer props))))
          (value [x]
            (cond (keyword? x) (name x)
                  (number? x) x
                  (string? x) (str "'" x "'")
                  (vector? x) ((joiner " ") x)
                  (map? x) (units x)
                  (set? x) ((joiner ",") x)
                  (fn? x) (x)
                  :else (doto x #(js/alert (pr-str {:css-value %})))))
          (prefix [property setting]
            (let [suffix (first property) prefixes (rest property)]
              (for [p (map #(str (name %) (name suffix)) (cons "" prefixes))]
                [p setting])))
          (convert [props]
            (for [[k v] props]
              (cond (keyword? k) [[(name k) (value v)]]
                    (string? k) [[k (value v)]]
                    (vector? k) (prefix k (value v))
                    :else (js/alert (pr-str [:css-map { :k k :v v}])))))
          (expand [props]
            (str "{"
                 (s/join (map (fn [[k v]] (str k ":" v ";"))
                              (apply concat (convert props))))
                 "}"))]
    (str (cond (keyword? select) (name select)
               (string? select) select
               (vector? select) ((joiner " ") select)
               (set? select) ((joiner ",") select)
               (fn? select) (select)
               :else (doto select #(js/alert (pr-str {:css-select %}))))
         (cond (string? props) props
               (keyword? props) (name props)
               (map? props) (expand props)
               (fn? props) (props)
               :else (doto props #(js/alert (pr-str {:css-rules %})))))))

(defn append-child!
  "Append HTML element child to parent."
  [parent child]
  (let [parent (get {:head (aget js/document "head")
                     :body (aget js/document "body")}
                    parent parent)]
    (.appendChild parent child)))

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
  (let [make (get {:html #(aget % "documentElement")
                   :head #(aget % "head")
                   :body #(aget % "body")} tag
                   #(.createElement % (name tag)))]
    (fn [attributes & kids]
      (let [element (make js/document)]
        (set-attributes element attributes)
        (doseq [kid kids]
          (cond (instance? js/Element kid) (append-child! element kid)
                (fn? kid) (js/alert "(fn? kid)")
                :else (append-child! element
                                     (.createTextNode js/document kid))))
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
  "Nil or the element with id."
  ([id]
   (.getElementById js/document (name id))))

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

(defn new-mark-for-guy
  "Drop a marker for guy on :the-map."
  [guy]
  (google.maps.Marker.
   (clj->js (assoc guy
                   :map (:the-map @state)
                   :icon (goog-map-micon (:color guy))
                   :visible true))))

(defn show-all-guys
  "Adjust the map so that all the markers are on it."
  []
  (let [state @state]
    (.fitBounds (:the-map state)
                (new-goog-bound
                 (map (comp new-goog-latlng :position)
                      (vals (:all state)))))))

(defn new-map-guys
  "A new map showing all the guys."
  [guys]
  (let [bound (new-goog-bound (map (comp new-goog-latlng :position) guys))
        bottom-right google.maps.ControlPosition.BOTTOM_RIGHT
        options {:center (.getCenter bound)
                 :zoom 8
                 :panControlOptions {:position bottom-right}
                 :zoomControlOptions {:position bottom-right}}
        result (or (element-by-id :the-map) (div {:id :the-map}))
        the-map (new-goog-map result options)]
    (.fitBounds the-map bound)
    (let [markers
          (reduce conj {}
                  (for [g guys] [(:id g) (new-mark-for-guy g)]))]
      (swap! state assoc :the-map the-map))
    result))

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
  (let [id (:id guy) title (:title guy)]
    (div {:id title :class :guy}
         (span {}
               (let [checkbox (input {:type :checkbox})
                     click #(set-visible! id (aget checkbox "checked"))]
                 (when (marker-visible? id) (aset checkbox "checked" true))
                 (doto checkbox (goog.events/listen "click" click)))
               (goog-icon-img-for guy)
               (if (= (get-in @state [:you :id]) id)
                 (let [text (input {:type :text :value title})
                       keyup #(my-name-is! (aget text "value"))]
                   (doto text (goog.events/listen "keyup" keyup)))
                 title)))))

(declare render-legend)

(defn render-you
  "Render the abbreviated you legend."
  []
  (let [state @state
        the-map (:the-map state) you (:you state) title (:title you)
        checkbox (input {:type :checkbox :disabled true})
        icon (goog-icon-img-for you)
        control (div {:id :you :class "legend"}
                     (div {:id title :class :guy}
                          (span {} checkbox icon title)))]
    (when (marker-visible? (:id you)) (aset checkbox "checked" true))
    (goog.events/listenOnce control "click" render-legend)
    (doto (aget the-map "controls" google.maps.ControlPosition.TOP_LEFT)
      (.pop)
      (.push control))))

(defn render-legend
  "Render the legend."
  []
  (let [state @state the-map (:the-map state) you (:you state)
        guys (cons you (remove #(= (:id you) (:id %)) (vals (:all state))))
        close (span {:id :close :class :guy :align :right}
                    (button {:type :button} "Close"))
        buttons (div {:class :buttons}
                     (span {:class :guy :align :left}
                           (doto (button {:type :button} "Where is everyone?")
                             (goog.events/listen "click" show-all-guys)))
                     close)
        control (apply (partial div {:id :legend :class "legend"})
                       (concat (for [guy guys] (legend-for-guy guy))
                               (list buttons)))]
    (goog.events/listenOnce close "click" render-you)
    (doto (aget the-map "controls" google.maps.ControlPosition.TOP_LEFT)
      (.pop)
      (.push control))))

(defn style-webkit-refresh-workaround
  "A style element to work around refresh problems in webkit."
  []
  (style {:id :webkit-refresh-workaround}
         (css ["@-webkit-keyframes" :androidBugfix]
              (let [padding "{ padding: 0; }"
                    from-to ["{" "from" padding "to" padding "}"]]
                (constantly (s/join " " from-to))))
         (css :body {:-webkit-animation [:androidBugfix :infinite {:s 1}]})))

(defn style-other-elements-on-page
  "A style element for the remaining necessary CSS."
  []
  (letfn [(prefix [prop] [prop :-webkit- :-moz- :-ms- :-o-])]
    (let [full {:% 100} full-page {:height full :width full}]
      (style {}
             (css :* {:box-sizing :border-box
                      :font [{:px 15} :arial \, :sans-serif]})
             (css #{:html :body} (assoc full-page :margin 0))
             (css :#the-map full-page)
             (css :.legend {:background :#fff
                            :display :flex
                            (prefix :flex) :auto
                            :flex-direction :column
                            :overflow :auto
                            :min-height {:px 0}
                            (prefix :transition) [:all {:s 0.25} :ease-out]})
             (css :img.legend-icon {:vertical-align :middle})
             (css :.guy {:margin {:px 10}})
             (css :.buttons {:margin {:px 5}})))))

(defn http-post
  "POST request to uri then pass response to handle-response."
  [uri request handle-response]
  (let [connection (goog.net.XhrIo.)]
    (goog.events.listen connection goog.net.EventType/COMPLETE
                        #(handle-response
                          (reader/read-string
                           (.getResponseText connection))))
    (.send connection uri "POST" request
           (clj->js {:content-type "application/edn"}))))

(defn update-markers
  "Use response to update markers.  Add new markers as necessary,
  then update position of live markers, and null out the map in the
  dead markers."
  [response]
  (when response
    (letfn [(ensure-markers [old]
              (reduce-kv
               (fn [result id guy]
                 (assoc result id
                        (or (get-in old [:markers id])
                            (new-mark-for-guy guy))))
               {} (:all response)))
            (handle [old]
              (let [alive (keys (:all response))
                    markers (ensure-markers old)]
                (doseq [id alive]
                  (.setPosition
                   (get markers id)
                   (clj->js (get-in response [:all id :position]))))
                (doseq [id (remove (set alive) (keys (:all old)))]
                  (.setMap (get-in old [:markers id]) nil))
                (assoc (merge old response) :markers markers)))]
      (swap! state handle))))

(defn update-state
  "Update the server with your current position."
  []
  (letfn [(handle [position]
            (let [state (swap! state assoc-in [:you :position]
                               {:lat (aget position "coords" "latitude")
                                :lng (aget position "coords" "longitude")})
                  uri (str "/update/" (or (:map-id state) 0) "/")]
              (http-post uri (pr-str (:you state)) update-markers)))]
    (try (-> js/navigator
             (aget "geolocation")
             (.getCurrentPosition handle js/alert))
         (catch js/Error e (.log js/console e)))))

(defn call-periodically-when-visible
  "Call f with args every ms milliseconds when document is visible."
  ([ms f]
   (letfn [(call [id]
             (let [id (if (aget js/document "hidden")
                        (and id (js/clearInterval id) nil)
                        (js/setInterval f ms))]
               (goog.events/listenOnce
                js/document "visibilitychange" #(call id))))]
     (call nil)))
  ([ms f x & args]
   (call-periodically-when-visible (fn [] (apply f x args)) ms)))

(defn page
  "Render the page HTML."
  [state]
  (html {}
        (head {}
              (link {:rel :icon :type "image/png" :href "moot.png"})
              ((element-for-tag :meta) {:charset :utf-8})
              (title {} "Where is everyone?")
              (style-webkit-refresh-workaround)
              (style-other-elements-on-page))
        (body {} (new-map-guys (vals (:all state)))))
  (render-you)
  (call-periodically-when-visible (* 5 1000) update-state))

(goog.events/listenOnce js/window "load" #(page @state))

(println [:RELOADED 'client])
