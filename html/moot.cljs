(ns moot)

(defn log
  [o]
  (js/alert "LOG")
  (.log js/console (pr-str o)))

(defn set-attributes
  [element option-map]
  (doseq [[k v] option-map]
    (.setAttribute element (name k) (name v))))

(defn new-map
  []
  (let [div (.createElement js/document "div")
        center {:lat -34.397, :lng 150.644}
        options {:center center :zoom 8}]
    (js/alert "new-map")
    (set-attributes div {:id "map-canvas"})
    (.appendChild (.-body js/document) div)
    (google.maps.Map. div (clj->js options))))

(defn load-map
  []
  (let [key "AIzaSyA10Vv0gV5yLp1WcTpqoA9hhILt_Rhc6OQ"
        api "https://maps.googleapis.com/maps/api/js?&v=3"
        script (.createElement js/document "script")
        src (str api "&key=" key "&signed_in=true")]
    (set-attributes script {:type "text/javascript" :src src})
    (.appendChild (.-head js/document) script)))

(set! (.-onload js/window) load-map)
