(ns moot.css
  (:require [clojure.string :as s]))

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
                    :else (js/alert (pr-str [:css-map {:k k :v v}])))))
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
