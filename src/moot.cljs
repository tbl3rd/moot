(ns moot)

(let [c (.. js/document (createElement "DIV"))]
  (aset c "innerHTML" "<p>from https://github.com/adzerk/boot-cljs-example/</p>")
  (.. js/document (getElementById "container") (appendChild c)))

(js/alert "Hi!")
