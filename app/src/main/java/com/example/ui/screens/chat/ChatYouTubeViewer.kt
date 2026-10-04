@@
-    val videoUrl = "https://www.youtube.com/watch?v=$videoId"
+    val videoUrl = YouTubeClient.buildVideoUrl(videoId)
@@
-                <iframe 
-                    src="https://www.youtube.com/embed/$videoId?autoplay=1&playsinline=1&fs=1&rel=0&modestbranding=1&enablejsapi=1" 
+                <iframe 
+                    src="${YouTubeClient.buildEmbedUrl(videoId)}" 
                     frameborder="0" 
