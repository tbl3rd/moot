(ns moot.client
  (:import [google.maps.event])
  (:require [moot.css :refer [css]]
            [moot.html :refer [element-for-tag body button div head html
                               img input label script span style title]]
            [cljs.reader :as reader]
            [clojure.string :as s]
            [goog.dom :as goog.dom]
            [goog.events :as goog.events]
            [goog.net.EventType :as goog.net.EventType]
            [goog.net.XhrIo :as goog.net.XhrIo]))

(def where "Where is everyone?")

(defn log
  "Log msg on the console."
  [msg]
  (.log js/console (pr-str msg)))

(def state
  "The state of the client."
  (atom nil))

(defn my-name-is!
  "Update your name everywhere it matters."
  [name]
  (let [id (get-in @state [:you :id])
        state (swap! state
                     (fn [state]
                       (-> state
                           (assoc-in [:you :title] name)
                           (assoc-in [:all id :title] name))))]
    (when-let [map-id (:map-id state)]
      (.setItem js/localStorage (str map-id) name))
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

(defn goog-map-icon
  "The icon with color."
  [color]
  (let [base "http://maps.google.com/mapfiles/ms/icons/"]
    (str base (name color) ".png")))

(defn goog-map-micon
  "The micon with color.  (Not sure what the icon/micon difference is.)"
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

(defn add-map-control
  "Put CONTROL in top left of map."
  [control]
  (let [corner (aget (:the-map @state) "controls"
                     google.maps.ControlPosition.TOP_LEFT)]
    (if (> (.getLength corner) 0) (.pop corner))
    (.push corner control)))

(declare render-legend)

(defn render-you
  "Render the abbreviated you legend."
  []
  (let [state @state
        you (:you state)
        title (:title you)]
    (when (and state you title)
      (let [checkbox (input {:type :checkbox :disabled true})
            icon (goog-icon-img-for you)
            control (div {:id :you :class "legend"}
                         (div {:id title :class :guy}
                              (span {} checkbox icon title)))]
        (when (marker-visible? (:id you)) (aset checkbox "checked" true))
        (goog.events/listenOnce control "click" render-legend)
        (add-map-control control)))))

(defn render-legend
  "Render the legend."
  []
  (let [state @state
        you (:you state)
        guys (cons you (remove #(= (:id you) (:id %)) (vals (:all state))))
        close (span {:id :close :class :guy :align :right}
                    (button {:type :button} "Close"))
        buttons (div {:class :buttons}
                     (span {:class :guy :align :left}
                           (doto (button {:type :button} where)
                             (goog.events/listen "click" show-all-guys)))
                     close)
        control (apply (partial div {:id :legend :class "legend"})
                       (concat (for [guy guys] (legend-for-guy guy))
                               (list buttons)))]
    (goog.events/listenOnce close "click" render-you)
    (add-map-control control)))

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

(defn get-meta-name-content-map
  "A map of name to content for all meta elements with name attributes."
  []
  (let [elements (.getElementsByTagName js/document "meta")
        metas (for [i (range (aget elements "length"))] (.item elements i))
        nvs (for [m metas] [(aget m "name") (aget m "content")])]
    (into {} (remove (comp empty? first) nvs))))

(defn ensure-markers
  "A :markers map containing old markers in STATE
  and all new markers from RESPONSE."
  [state response]
  (reduce-kv
   (fn [result id guy]
     (assoc result id
            (or (get-in state [:markers id])
                (new-mark-for-guy guy))))
   {} (:all response)))

(defn move-markers
  "Move markers in STATE according to positions in RESPONSE."
  [state response]
  (let [alive (keys (:all response))
        markers (ensure-markers state response)]
    (doseq [id alive]
      (.setPosition
       (get markers id)
       (clj->js (get-in response [:all id :position]))))
    (doseq [id (remove (set alive) (keys (:all state)))]
      (if-let [marker (get-in state [:markers id])]
        (.setMap marker nil)))
    (assoc (merge state response) :markers markers)))

(defn update-markers
  "Use response to update markers."
  [response]
  (when response
    (let [first-update? (nil? (:markers @state))]
      (swap! state move-markers response)
      (when first-update?
        (show-all-guys)
        (render-you)))))

(defn update-position
  "Call handler with the current position."
  [handler]
  (try
    (.getCurrentPosition (aget js/navigator "geolocation") handler js/alert)
    (catch :default x (js/alert x))))

(defn update-state
  "Update client state after sending position to server."
  []
  (letfn [(handler [position]
            (let [coords (aget position "coords")
                  state (swap! state assoc-in [:you :position]
                               {:lat (aget coords "latitude")
                                :lng (aget coords "longitude")})
                  uri (str "/update/" (:map-id state) "/")]
              (http-post uri (pr-str (:you state)) update-markers)))]
    (update-position handler)))

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

(defn new-goog-map
  "A new Google map centered on the current position."
  []
  (let [result (or (.getElementById js/document "the-map") (div {:id :the-map}))
        bottom-right {:position google.maps.ControlPosition.BOTTOM_RIGHT}]
    (letfn [(handler [position]
              (let [coords (aget position "coords")
                    latlng {:lat (aget coords "latitude")
                            :lng (aget coords "longitude")}
                    options {:zoom 13 :center (clj->js latlng)
                             :panControlOptions bottom-right
                             :zoomControlOptions bottom-right}
                    the-map (google.maps.Map. result (clj->js options))]
                (swap! state #(-> %
                                  (assoc-in [:you :position] latlng)
                                  (assoc :the-map the-map)))
                (google.maps.event.addListenerOnce
                 the-map "idle"
                 #(call-periodically-when-visible (* 5 1000) update-state))))]
      (update-position handler)
      result)))

(defn frob-url-in-address-bar
  "Put the map-id from the HTML meta element into address bar and STATE."
  []
  (let [map-id (get (get-meta-name-content-map) "moot-map-id")
        uri (str "/update/" map-id "/")]
    (swap! state assoc :map-id map-id)
    (.replaceState js/history (js-obj) where uri)))

(defn render-page
  "Render the page HTML."
  []
  (frob-url-in-address-bar)
  (html {}
        (head {}
              (title {} where)
              (style-webkit-refresh-workaround)
              (style-other-elements-on-page))
        (body {} (new-goog-map))))

(goog.events/listenOnce js/window "load" render-page)

(println [:RELOADED 'client])
