# ImageViewPlus
ImageViewPlus is an extension of ImageView to enhance its functionality.
 - Top-aligned scale mode to enable a top-aligned image inside of an ImageViewPlus (`top_crop`)
 - Layer drawable defines the wrapper drawable to use for content. This is useful for adding a selector for states on the ImageView when the states have overlay on the content drawable
 - Default drawable (for when no drawable is set)

## Usage
To use `ImageViewPlus` in your layout:
 0. Add the ImageViewPlus project as a library reference to your project
 0. Define your layer drawable (`res/drawable/image_selector.xml` in this example)

        <?xml version="1.0" encoding="utf-8"?>
        <layer-list xmlns:android="http://schemas.android.com/apk/res/android" >
            <item android:id="@+id/content_drawable">
                <shape android:shape="rectangle" />
            </item>
            <item>
                <selector>
                    <item android:state_pressed="true">
                        <shape android:shape="rectangle" >
                            <solid android:color="#3000" />
                        </shape>
                    </item>
                    <item>
                        <shape android:shape="rectangle" >
                            <solid android:color="#0fff" />
                        </shape>
                    </item>
                </selector>
            </item>
        </layer-list>

     This particular drawable shows a semi-transparent black overlay on the content when pressed

 0. Define your layout

        <?xml version="1.0" encoding="utf-8"?>
        <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:app="http://schemas.android.com/apk/res/com.example.app"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:orientation="vertical" >
            
                <com.meetme.android.imageviewplus.ImageViewPlus
                    android:id="@+id/top_crop_image"
                    android:layout_width="match_parent"
                    android:layout_height="210dp"
                    android:src="@drawable/example_image"
                    app:scaleType="fitCenter"
                    app:layerDrawable="@drawable/image_selector"
                    app:contentLayerId="@id/content_drawable" />
            
        </LinearLayout>
    
    **Important**: The `xmlns:app="http://schemas.android.com/apk/res/com.example.app"` line needs to use the namespace of your application for the `com.example.app` portion to function properly.

## Notes

 * `ImageViewPlus#getDrawable()` will return the drawable of the __content__, not the layer drawable (if used). This is done to protect existing usage that assume `setImage*` methods and `getDrawable()` use the same underlying field (such as [Google's ImageFetcher example](http://developer.android.com/training/displaying-bitmaps/index.html)).
 * With the aforementioned ImageFetcher example, the default drawable implementation may not function as expected. ImageFetcher sets a `Drawable` (`AsyncDrawable`) on the attached ImageView instance which we do not introspect or deal with in any way.
 * The default drawable is set to the content when the content drawable is not set (using one of the `setImage*` methods).

## Contributors
 - [Dallas Gutauckis](http://github.com/dallasgutauckis)
 - [Joe Hansche](http://github.com/madCoder)

## License
ImageViewPlus is provided under the Apache 2.0 license:

    Copyright 2013 MeetMe, Inc.
    
    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at
    
        http://www.apache.org/licenses/LICENSE-2.0
    
    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

## Contributing
To make contributions, fork this repository, commit your changes, and submit a pull request.