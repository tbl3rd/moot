(ns moot.html)

(defn- set-attributes
  "Set attributes on element according to attribute-map."
  [element attribute-map]
  (doseq [[k v] attribute-map]
    (let [value (cond (true? v) "true"
                      (false? v) "false"
                      (number? v) (str v)
                      (string? v) v
                      (keyword? v) (name v)
                      (fn? v) v
                      :else (js/alert (pr-str v)))]
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
          (cond (instance? js/Element kid) (.appendChild element kid)
                (fn? kid) (js/alert "(fn? kid)")
                :else (.appendChild element
                                     (.createTextNode js/document kid))))
        element))))

(def body     (element-for-tag :body))
(def button   (element-for-tag :button))
(def div      (element-for-tag :div))
(def form     (element-for-tag :form))
(def head     (element-for-tag :head))
(def html     (element-for-tag :html))
(def img      (element-for-tag :img))
(def input    (element-for-tag :input))
(def label    (element-for-tag :label))
(def script   (element-for-tag :script))
(def span     (element-for-tag :span))
(def style    (element-for-tag :style))
(def title    (element-for-tag :title))
