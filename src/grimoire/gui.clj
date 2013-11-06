(ns grimoire.gui
  (:import (javafx.scene.input Clipboard ClipboardContent)
           (javafx.application Application Platform)
           (javafx.scene Node Scene)
           (javafx.scene.web WebView)
           (javafx.scene.input KeyCode)
           (javafx.scene.text Text Font FontWeight)
           (javafx.scene.control Label TextField PasswordField Button Hyperlink ListView TabPane Tab ContextMenu MenuItem ListCell)
           (java.lang Runnable)
           (java.io File)
           (javafx.util Callback)
           (javafx.scene.layout GridPane HBox VBox Priority)
           (javafx.scene.paint Color)
           (javafx.scene.image Image ImageView)
           (javafx.geometry Pos Insets)
           (javafx.event EventHandler)
           (javafx.stage Stage Modality Popup)
           (javafx.scene.web WebView)
           (javafx.collections FXCollections ObservableList)
           (javafx.fxml FXML FXMLLoader))
  (:use [grimoire.oauth]
        [grimoire.services :only [start stop gen-twitterstream]]
        [grimoire.data]
        [grimoire.plugin]
        [grimoire.commands]
        [grimoire.listener]
        [clojure.string :only [split]])
  (:require [clojure.java.io :as io])
  (:gen-class
   :extends javafx.application.Application))

(defn gen-profile
  "Generate profile window, user: twitter4j.User instance"
  [user]
  (let [image (doto (ImageView. (. user getBiggerProfileImageURL))
                (.setFitHeight 78)
                (.setFitWidth 78))
        lbl (doto (Label. (. user getScreenName))
              (.setFont (Font. 20))
              (.setId "label"))
        hl (doto (Hyperlink. (. user getURL))
             (.setOnAction 
               (proxy [EventHandler] []
                 (handle [_]
                   (gen-webview (. user getURL))))))
        vbox (doto (VBox.)
               (.setAlignment Pos/CENTER)
               (.setSpacing 20)
               (.. getChildren (add image))
               (.. getChildren (add lbl))
               (.. getChildren (add hl)))
        ol (FXCollections/observableArrayList
             (.getUserTimeline twitter (. user getId)))
        lv (ListView. ol)
        tweets (doto (Tab. "Tweets")
                 (.setContent lv))
        tabpane (doto (TabPane.)
                  (.. getChildren (add tweets)))
        root (doto (VBox.)
               (.. getChildren (add vbox))
               (.. getChildren (add tabpane)))
        scene (Scene. root 400 600)
        stage (doto (Stage.)
                (.setScene scene))]
      (do
        (VBox/setVgrow lv Priority/ALWAYS)
        (HBox/setHgrow lv Priority/ALWAYS)
        stage)))

(defn gen-pane
  "Generate Pane, image: label's image url, lbl: Pane title, coll: listview's collection"
  [#^java.lang.String image #^java.lang.String title #^ObservableList coll]
  (let [lbl (doto (Label. title)
              (.setId "label")
              (.setFont (Font. 20)))
        image (doto (ImageView. image)
                (.setFitHeight 20)
                (.setFitWidth 20))
        hbox (doto (HBox.)
               (.setId "node")
               (.setSpacing 5)
               (.. getChildren (add image))
               (.. getChildren (add lbl)))
        replymenu (MenuItem. "Reply")
        favmenu (MenuItem. "Favorite")
        retmenu (MenuItem. "Retweet")
        favretmenu (MenuItem. "Fav&Retweet")
        conm (doto (ContextMenu.) 
               (.. getItems (add replymenu))
               (.. getItems (add favmenu))
               (.. getItems (add retmenu))
               (.. getItems (add favretmenu)))
        ;cf (reify Callback 
        ;     (call [this _]
        ;       #^ListCell
        ;       (proxy [ListCell] []
        ;         (updateItem [#^twitter4j.Status status #^java.lang.Boolean emp?]
        ;           (proxy-super updateItem status emp?)
        ;           (future
        ;             (add-runlater
        ;               (if (not emp?)
        ;                 (.setGraphic this
        ;                   (gen-node! #^twitter4j.Status status)))))))))
        listv (doto (ListView.)
                (.setId (str title "-tllv"))
                (.setItems coll)
                (.setContextMenu conm))
                ;(.setCellFactory cf))
        root (doto (VBox.)
               (.setPrefWidth 100)
               (.setPrefHeight 200)
               (.. getChildren (add hbox))
               (.. getChildren (add listv)))]
    (do
      (HBox/setHgrow hbox Priority/ALWAYS)
      (HBox/setHgrow listv Priority/ALWAYS)
      (VBox/setVgrow listv Priority/ALWAYS)
      (HBox/setHgrow root Priority/ALWAYS)
      
      (.setOnAction favmenu
        (proxy [EventHandler] []
          (handle [_]
            (if @nervous
              (bool-dialog 
                (str "Are you sure want to Favorite?\n"
                     (. (focused-status listv) getText))
                     "Sure"
                     #(fav)
                     "Cancel"))
            (fav))))
      (.setOnAction retmenu
        (proxy [EventHandler] []
          (handle [_]
            (if @nervous
              (bool-dialog 
                (str "Are you sure want to Favorite?\n"
                     (. (focused-status listv) getText))
                     "Sure"
                     #(ret)
                     "Cancel")
              (ret)))))
      (.setOnAction favretmenu
        (proxy [EventHandler] []
          (handle [_]
            (if @nervous
              (bool-dialog 
                (str "Are you sure want to Favorite & Retweet?\n"
                     (. (focused-status listv) getText))
                     "Sure"
                     #(favret)
                     "Cancel")
              (favret)))))
      (.setOnContextMenuRequested listv
                  (proxy [EventHandler] [] 
                    (handle [e] 
                      (let [urls (filter #(= (seq "http") (take 4 %)) (split (.. (focused-status listv) getText) #"\s|\n|　"))
                            mis (map 
                                  #(doto (MenuItem. %)
                                    (.setOnAction
                                      (proxy [EventHandler] []
                                        (handle [_]
                                          (gen-webview %)))))
                                  urls)
                            plgs (map 
                                  #(if (. % get-name)
                                     (doto (MenuItem. (. % get-name))
                                       (.setOnAction
                                         (proxy [EventHandler] []
                                           (handle [e]
                                             (.on-click % e))))))
                                   @plugins)]
                        (add-runlater
                          (do
                            (.. conm getItems (remove 4 (.. conm getItems size)))
                            (doall
                              (map #(.. conm getItems (add %))
                                (concat mis plgs)))))))))
      (.setOnKeyPressed root
        (proxy [EventHandler] []
          (handle [ke]
            (try
              ((@key-maps [(.. ke getCode getName) (.isControlDown ke) (.isShiftDown ke)]) listv)
              (catch Exception e (println (.getMessage e)))))))                 
      root)))


; main window
; dirty
(defn mainwin
  [^Stage stage]
  (let [; load fxml layout
        root (-> "main.fxml" io/resource FXMLLoader/load)
        scene (Scene. root 800 600)
        mentions (reverse (.getMentions twitter))]
    (do
      ; load rcfile
      (future
        (binding [*ns* (find-ns 'grimoire.gui)]
          (try (load-file 
                 (str (get-home)
                   "/.grimoire.clj"))
            (catch Exception e (println e)))))
      ; set name
      (reset! myname (. twitter getScreenName))
      ; backup scene
      (reset! mainscene scene)
      ; Add mentions pane
      (add-runlater
        (.. scene (lookup "#pane") getChildren (add 
          (gen-pane "reply_hover.png" "Mentions" mention-nodes))))
      ; add mentioins tweets
      (dosync
        (alter tweets (comp vec concat) mentions))
      (doall
        (map #(add-nodes! nodes %) mentions))
      (doall
        (map #(add-nodes! mention-nodes %) mentions))
      (doto stage 
        (.setTitle "Grimoire - v0.1.2")
        (.setScene scene)
        .show)
      ; theme setting
      (set-theme @theme)
      (try
        (doall
          (map #(.on-start %)
            @plugins))
        (catch Exception e (print-node! (.getMessage e)))))))

; javafx start
; dirty
(defn -start [this ^Stage stage]
  (let [signup (-> "signin.fxml" io/resource FXMLLoader/load)]
    (do
      (get-oauthtoken!)
      (reset! main-stage stage)
      (if (get-tokens)
        (do 
          ; send stage to fxml
          (get-tokens)
          (gen-twitter)
          (gen-twitterstream listener)
          (start)
          (mainwin stage))
        ; start sign up scene 
        (doto stage
          (.setTitle "Twitter Sign up")
          (.. getIcons (add (Image. "bird_blue_32.png" (double 32) (double 32) true true)))
          (.setScene (Scene. signup 600 400))
          .show)))))
