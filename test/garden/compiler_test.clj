(ns garden.compiler_test
  (:require [clojure.test :refer :all]
            [garden.compiler :refer :all]
            [garden.types])
  (:import (garden.types CSSFunction
                         CSSImport
                         CSSKeyframes)))

(deftest render-css-test 
  (testing "maps"
    (are [x y] (= (render-css (expand  x)) y)
      {:a 1} "a: 1;"
      {:a "b"} "a: b;" 
      {:a :b} "a: b;"
      {:a {:b {:c 1}}} "a-b-c: 1;"
      {:a (sorted-set 1 2 3)} "a: 1;\na: 2;\na: 3;"
      {:a [1 2 3]} "a: 1, 2, 3;"
      {:a [[1 2] 3 [4 5]]} "a: 1 2, 3, 4 5;"))

  (testing "vectors"
    (are [x y] (= (compile-css (list  x)) y)
      [:a {:x 1} {:y 2}]
      "a{x:1;y:2}"

      [:a {:x 1} [:b {:y 2}]]
      "a{x:1}a b{y:2}"

      [:a :b {:x 1} [:c :d {:y 2}]]
      "a,b{x:1}a c,a d,b c,b d{y:2}"

      [:a :b
       '({:x 1})
       '([:c :d {:y 2}])]
      "a,b{x:1}a c,a d,b c,b d{y:2}"

      [[:a {:x 1}] [:b {:y 2}]]
      "a{x:1}b{y:2}")))

(deftest media-query-test
  (testing "media queries"
    (are [xs res] (= (compile-css (list xs)) res)
      [(-> (list [:h1 {:a :b}])
           (with-meta {:screen true}))]
      "h1{a:b}"

      [(-> (list [:h1 {:a :b}])
           (with-meta {:media {:screen true}}))]
      "@media screen{h1{a:b}}"

      [(-> (list [:h1 {:a :b}])
           (with-meta {:media {:screen true}}))
       [:h2 {:c :d}]]
      "@media screen{h1{a:b}}h2{c:d}"

      [[:a {:a "b"}
        ^{:media {:screen true}}
        [:&:hover {:c "d"}]]]
      "a{a:b}@media screen{a:hover{c:d}}"

      [^{:media {:toast true}}
       [:h1 {:a "b"}]]
      "@media toast{h1{a:b}}"

      [^{:media {:bacon :only}}
       [:h1 {:a "b"}]]
      "@media only bacon{h1{a:b}}"

      [^{:media {:sad false}}
       [:h1 {:a "b"}]]
      "@media not sad{h1{a:b}}"

      [^{:media {:happy true :sad false}}
       [:h1 {:a "b"}]]
      "@media happy and not sad{h1{a:b}}"

      [^{:media {:-vendor-prefix-blah-blah-blah "2"}}
       [:h1 {:a "b"}]]
      "@media(-vendor-prefix-blah-blah-blah:2){h1{a:b}}")))

(deftest parent-selector-test
  (testing "parent selector references"
    (are [x res] (= (compile-css (list x)) res)
      [:a [:&:hover {:x :y}]]
      "a:hover{x:y}"

      [:a [:& {:x :y}]]
      "a{x:y}"

      [:a
       ^{:media {:max-width "1em"}}
       [:&:hover {:x :y}]]
      "@media(max-width:1em){a:hover{x:y}}"

      ^{:media {:screen true}}
      [:a {:f "bar"}
       ^{:media {:print true}}
       [:& {:g "foo"}]]
      "@media screen{a{f:bar}}@media print{a{g:foo}}")))

(deftest type-render-test
  (testing "CSSFunction"
    (are [x res] (= (render-css x) res)
      (CSSFunction. :url "background.jpg")
      "url(background.jpg)"

      (CSSFunction. :daughter [:alice :bob])
      "daughter(alice, bob)"

      (CSSFunction. :x [(CSSFunction. :y 1) (CSSFunction. :z 2)])
      "x(y(1), z(2))"))

  (testing "CSSImport"
    (let [url "http://example.com/foo.css"]
      (are [x res] (= (render-css x) res)
        (CSSImport. url nil)
        "@import \"http://example.com/foo.css\";"

        (CSSImport. url {:screen true}) 
        "@import \"http://example.com/foo.css\" screen;"))))

(def test-vendors ["moz" "webkit"])

(deftest flag-tests
  (testing ":vendors"
    (let [rule [:a ^:prefix {:a 1 :b 1}]
          compiled (-> (list {:vendors test-vendors} rule)
                       (compile-css))]
      (is (re-find #"-moz-a:1;-webkit-a:1;a:1" compiled))
      (is (re-find #"-moz-b:1;-webkit-b:1;b:1" compiled)))

    (let [keyframes (CSSKeyframes. "fade" [[:from {:foo "bar"}]
                                           [:to {:foo "baz"}]])
          compiled (-> (list {:vendors test-vendors} keyframes)
                       (compile-css))]
      (is (re-find #"@-moz-keyframes" compiled))
      (is (re-find #"@-webkit-keyframes" compiled))
      (is (re-find #"@keyframes" compiled)))))
