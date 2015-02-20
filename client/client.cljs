(ns client
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
    (swap! state
           (fn [state]
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
          (cond (instance? js/Element kid) (append-child element kid)
                (fn? kid) (js/alert "(fn? kid)")
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

(defn show-all-guys
  "Adjust the map so that all the markers are on it."
  []
  (let [state @state]
    (.fitBounds (:the-map state)
                (new-goog-bound
                 (map (comp new-goog-latlng :position) (:all state))))))

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
    (doseq [guy guys] (mark-guy the-map guy :drop))
    (swap! state #(update-in % [:the-map] (constantly the-map)))
    #_(js/alert (pr-str {:the-map (:the-map @state)}))
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

(defn show-legend!
  "Show the legend if show?.  Otherwise hide it."
  [show?]
  (let [table {:legend {true "flex" false "none"}
               :you    {true "none" false "flex"}}]
    (letfn [(setter!
              [id show]
              (aset (element-by-id id) "style" "display"
                    (get-in table [id show])))]
      (setter! :legend show?)
      (setter! :you    show?))))

(defn render-you
  "Render the abbreviated you legend."
  [state]
  (let [you (:you state)
        title (:title you)
        result (div {:id :you :class "legend"}
                    (div {:id title :class :guy}
                         (span {}
                               (input {:type :checkbox
                                       :disabled true
                                       :checked  (marker-visible? (:id you))})
                               (goog-icon-img-for you)
                               title)))]
    (goog.events/listen result "click" #(show-legend! true))
    result))

(defn render-legend
  "Render the legend according to state."
  [state]
  (let [you (:you state)
        guys (cons you (remove #(= (:id you) (:id %)) (:all state)))
        close (span {:id :close :class :guy :align :right}
                    (button {:type :button} "Close"))
        buttons (div {:class :buttons}
                     (span {:class :guy :id :close :align :left}
                           (doto (button {:type :button} "Where is everyone?")
                             (goog.events/listen "click" show-all-guys)))
                     close)
        result (apply (partial div {:id :legend :class "legend"})
                      (concat (for [guy guys] (legend-for-guy guy))
                              (list buttons)))]
    (goog.events/listen close "click" #(show-legend! false))
    result))

(defn style-webkit-refresh-workaround
  "A style element to workaround refresh problems in webkit."
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
                            (prefix :flex) [1 1 :auto]
                            :flex-direction :column
                            :overflow :auto
                            :min-height {:px 0}
                            :pointer-events :inherit
                            :position :fixed
                            :z-index 99
                            (prefix :transition) [:all {:s 0.25} :ease-out]})
             (css :img.legend-icon {:vertical-align :middle})
             (css :.guy {:margin {:px 10}})
             (css :.buttons {:margin {:px 5}})))))

(defn http-post
  "POST request map to uri then pass response to handle-response."
  [uri request handle-response]
  (let [connection (new goog.net.XhrIo)]
    (goog.events.listen connection goog.net.EventType/COMPLETE
                        #(handle-response
                          (cljs.reader/read-string
                           (. connection getResponseText))))
    (. connection send uri "POST" request
       (clj->js {"Content-type" "text/plain"}))))

(defn update-location
  []
  (http-post "/update" (:you @state) js/alert))

(defn page
  "Render the page HTML."
  [state]
  (html {}
        (head {}
              ((element-for-tag :meta) {:charset :utf-8})
              (title {} "Where is everyone?")
              (style-webkit-refresh-workaround)
              (style-other-elements-on-page))
        (body {}
              (render-you state)
              (render-legend state)
              (new-map-guys (:all state))))
  (show-legend! false)
  (update-location))

(goog.events/listen js/window "load" #(page @state))
