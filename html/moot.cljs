(ns moot
  (:require
   [goog.events :as goog.events]))

(defn log
  "Log msg on the console."
  [msg]
  (.log js/console (pr-str msg)))

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

(defn element
  "An element with tag."
  [tag]
  (fn [attributes & kids]
    (let [e (create-element tag attributes)]
      (doseq [k kids] (.appendChild e k))
      e)))

(def div (element :div))

(defn new-google-map
  [element options]
  (google.maps.Map. element (clj->js options)))

(defn new-map
  []
  (let [div (create-element :div {:id "map"})]
    (.appendChild (.-body js/document) div)
    (new-google-map div
                    {:center {:lat -34.397, :lng 150.644}
                     :zoom 8})))

(goog.events/listen js/window "load" new-map)
