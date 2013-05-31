/* {@formatter:off} */
/**
 * Copyright 2013 MeetMe, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/* {@formatter:on} */
package com.meetme.android.imageviewplus;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RectShape;
import android.net.Uri;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseArray;
import android.widget.ImageView;

import com.meetme.imageviewplus.R;

/**
 * ImageView implementation that refuses to draw if the referenced Bitmap has been recycled. It also provides a way to set a default Drawable that can
 * be used in the recycled Bitmap's place, or when no other resource has been applied to it.
 * <p />
 * Note that if the intended drawable does get recycled, and this view reverts to back to the default, the application will not be notified. However,
 * the {@link #isShowingDefaultDrawable()} method can be used to determine if the default is currently active.
 */
public class ImageViewPlus extends ImageView {
    private static final int CONTENT_LAYER_ID_EMPTY = -1;

    private static final String TAG = ImageViewPlus.class.getSimpleName();

    /**
     * The default drawable, used whenever nothing is set as <code>@src</code>, or when the current Drawable has been recycled.
     */
    private Drawable mDefaultDrawable = null;

    /**
     * The layer drawable to wrap the content
     *
     * @see R.attr#layerDrawable
     */
    private LayerDrawable mLayerDrawable;

    /**
     * The layer id in {@link #mLayerDrawable} to replace content with
     *
     * @see R.attr#contentLayerId
     */
    private int mContentLayerId = CONTENT_LAYER_ID_EMPTY;

    private DefaultDrawableListener mListener = null;

    private PlusScaleType mScaleType;

    private Drawable mContentDrawable;

    private int mContentResource;

    private Uri mContentUri;

    public ImageViewPlus(final Context context) {
        this(context, null);
    }

    public ImageViewPlus(final Context context, final AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ImageViewPlus(final Context context, final AttributeSet attrs, final int defStyle) {
        super(context, attrs, defStyle);

        if (attrs != null) {
            TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.ImageViewPlus, 0, 0);
            Drawable defaultDrawable = a.getDrawable(R.styleable.ImageViewPlus_defaultDrawable);
            Drawable selectorDrawable = a.getDrawable(R.styleable.ImageViewPlus_layerDrawable);
            int selectorLayerId = a.getResourceId(R.styleable.ImageViewPlus_contentLayerId, CONTENT_LAYER_ID_EMPTY);
            int scaleType = a.getInt(R.styleable.ImageViewPlus_scaleType, -1);

            a.recycle();

            if (scaleType >= 0) {
                setScaleType(PlusScaleType.getScaleType(scaleType));
            }

            if (defaultDrawable != null) {
                setDefaultDrawable(defaultDrawable);
            }

            if (selectorDrawable != null && selectorDrawable instanceof LayerDrawable) { // We don't check the value of the selectorLayerId as
                                                                                         // #setSelectorResources handles that check
                setLayerResources((LayerDrawable) selectorDrawable, selectorLayerId);
            }
        }
    }

    /**
     * Sets a drawable as the content of this ImageView.
     *
     * <p class="note">
     * This does Bitmap reading and decoding on the UI thread, which can cause a latency hiccup. If that's a concern, consider using
     * {@link #setImageDrawable(android.graphics.drawable.Drawable)} or {@link #setImageBitmap(android.graphics.Bitmap)} and
     * {@link android.graphics.BitmapFactory} instead.
     * </p>
     *
     * @param resId the resource identifier of the the drawable
     *
     * @attr ref android.R.styleable#ImageView_src
     */
    @Override
    public void setImageResource(int resId) {
        if (mContentUri != null || mContentResource != resId) {
            updateDrawable(null);
            mContentResource = resId;
            mContentUri = null;

            resolveUri();
        }
    }

    /**
     * Sets the content of this ImageView to the specified Uri.
     *
     * <p class="note">
     * This does Bitmap reading and decoding on the UI thread, which can cause a latency hiccup. If that's a concern, consider using
     * {@link #setImageDrawable(android.graphics.drawable.Drawable)} or {@link #setImageBitmap(android.graphics.Bitmap)} and
     * {@link android.graphics.BitmapFactory} instead.
     * </p>
     *
     * @param uri The Uri of an image
     */
    @Override
    public void setImageURI(Uri uri) {
        if (mContentResource != 0 ||
                (mContentUri != uri &&
                (uri == null || mContentUri == null || !uri.equals(mContentUri)))) {
            updateDrawable(null);
            mContentResource = 0;
            mContentUri = uri;

            resolveUri();
        }
    }

    /**
     * Sets a drawable as the content of this ImageView
     *
     * @param drawable The drawable to set
     */
    @Override
    public void setImageDrawable(Drawable drawable) {
        if (mContentDrawable != drawable) {
            mContentResource = 0;
            mContentUri = null;

            updateDrawable(drawable);
        }
    }

    /**
     * Returns the content drawable of this ImageView
     */
    @Override
    public Drawable getDrawable() {
        return mContentDrawable;
    }

    /**
     * Sets a Bitmap as the content of this ImageView.
     *
     * @param bm The bitmap to set
     */
    @Override
    public void setImageBitmap(Bitmap bm) {
        // if this is used frequently, may handle bitmaps explicitly
        // to reduce the intermediate drawable object
        setImageDrawable(new BitmapDrawable(getContext().getResources(), bm));
    }

    /**
     * @return the LayerDrawable used to wrap content, if any
     */
    public LayerDrawable getLayerDrawable() {
        return mLayerDrawable;
    }

    /**
     * Set the layer drawable and the id of the layer within the drawable to use for content
     *
     * @param layerDrawable
     * @param contentLayerId
     */
    public void setLayerResources(LayerDrawable layerDrawable, int contentLayerId) {
        if (layerDrawable != null && contentLayerId == CONTENT_LAYER_ID_EMPTY) {
            throw new IllegalArgumentException("Cannot use a selector drawable without specifying a selector layer id");
        }

        mContentLayerId = contentLayerId;
        mLayerDrawable = layerDrawable;

        updateDrawable(mContentDrawable);
    }

    /**
     * Sets the scale type to use when drawing content
     *
     * @param scaleType
     */
    public void setScaleType(PlusScaleType scaleType) {
        mScaleType = scaleType;

        if (scaleType.superScaleType != null) {
            // Use the scaling built in to the ImageView for any scale type which has a super scale type
            setScaleType(scaleType.superScaleType);
        } else {
            // Our own built-in scale types all use a custom MATRIX implementation
            setScaleType(ImageView.ScaleType.MATRIX);
        }
    }

    private void resolveUri() {
        if (mContentDrawable != null && mContentDrawable != mDefaultDrawable) {
            return;
        }

        Resources rsrc = getResources();
        if (rsrc == null) {
            return;
        }

        Drawable d = null;

        if (mContentResource != 0) {
            try {
                d = rsrc.getDrawable(mContentResource);
            } catch (Exception e) {
                // Don't try again.
                mContentUri = null;
            }
        } else if (mContentUri != null) {
            String scheme = mContentUri.getScheme();
            if (ContentResolver.SCHEME_ANDROID_RESOURCE.equals(scheme)) {
                /*
                 * FIXME Can't be used due to missing compilation unit for ContentResolver#getResourceId
                 */
                // try {
                // getContext().getResources().get
                // // Load drawable through Resources, to get the source density information
                // ContentResolver.OpenResourceIdResult r = getContext().getContentResolver().getResourceId(mContentUri);
                // getContext().getContentResolver().d = r.r.getDrawable(r.id);
                // } catch (Exception e) {
                // Log.w("ImageView", "Unable to open content: " + mUri, e);
                // }
            } else if (ContentResolver.SCHEME_CONTENT.equals(scheme) || ContentResolver.SCHEME_FILE.equals(scheme)) {
                try {
                    d = Drawable.createFromStream(getContext().getContentResolver().openInputStream(mContentUri), null);
                } catch (Exception e) {
                    Log.w("ImageView", "Unable to open content: " + mContentUri, e);
                }
            } else {
                d = Drawable.createFromPath(mContentUri.toString());
            }

            if (d == null) {
                System.out.println("resolveUri failed on bad bitmap uri: " + mContentUri);
                // Don't try again.
                mContentUri = null;
            }
        } else {
            return;
        }

        updateDrawable(d);
    }

    /**
     * Updates the ImageView to show the given {@link Drawable} as content, updating the selector drawable layer if one is provided
     *
     * @param drawable
     */
    private void updateDrawable(Drawable drawable) {
        if (drawable == null && mDefaultDrawable != null) {
            drawable = mDefaultDrawable;
        }

        mContentDrawable = drawable;

        if (mLayerDrawable != null) {
            if (drawable == null) {
                // We need a placeholder drawable for the LayerDrawable
                drawable = new ShapeDrawable(new RectShape());
            }

            // FIXME there is probably a better workaround for doing this to include re-requesting layoud if the content drawable is new and not
            // equal-dimension
            super.setImageDrawable(null);

            // Selector is there, find the id in the layer and update that layer's Drawable
            mLayerDrawable.setDrawableByLayerId(mContentLayerId, drawable);
            super.setImageDrawable(mLayerDrawable);
        } else {
            super.setImageDrawable(drawable);
        }
    }

    /**
     * @param mListener the DefaultDrawableListener to set
     */
    public void setListener(final DefaultDrawableListener listener) {
        this.mListener = listener;
    }

    /**
     * @return the DefaultDrawableListener
     */
    public DefaultDrawableListener getListener() {
        return mListener;
    }

    /**
     * Returns true if this view is referencing a BitmapDrawable, and its Bitmap reference has been recycled.
     *
     * @return
     */
    public boolean isDrawableRecycled() {
        final Drawable drawable = getDrawable();

        if (drawable instanceof BitmapDrawable) {
            final Bitmap bmp = ((BitmapDrawable) drawable).getBitmap();

            if (bmp != null) {
                return bmp.isRecycled();
            }
        } else if (drawable instanceof LayerDrawable) {
            // This is a somewhat shoddy implementation, but it's quick and gets the job done.
            final LayerDrawable layerDrawable = (LayerDrawable) drawable;

            // Iterate over the layers; look for a recycled bitmap
            for (int i = 0; i < (layerDrawable).getNumberOfLayers(); i++) {
                final Drawable iDrawable = layerDrawable.getDrawable(i);

                if (iDrawable instanceof BitmapDrawable) {
                    final BitmapDrawable bitmapDrawable = (BitmapDrawable) iDrawable;
                    final Bitmap bmp = bitmapDrawable.getBitmap();

                    if (bmp != null) {
                        if (bmp.isRecycled()) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    /**
     * Resets the ImageView resource back to the default.
     */
    public void resetToDefault() {
        setImageDrawable(mDefaultDrawable);
        clearColorFilter();
    }

    /**
     * Returns the view's default drawable.
     *
     * @return current default drawable
     * @see #setDefaultDrawable(Drawable)
     * @attr ref com.myyearbook.m.R.styleable#DownloadableImageView_defaultDrawable
     */
    public Drawable getDefaultDrawable() {
        return mDefaultDrawable;
    }

    /**
     * Returns <code>true</code> if the view is currently referencing the default drawable. If no default has been provided, this method returns
     * false, even if there is no super-class drawable (i.e., even if both Drawables are null, this method still returns false).
     * <p />
     * <b>Warning</b>: If both attributes refer to the same resource, this will return <code>false</code>. That is because
     * <code>android:src="@drawable/foo"</code> and <code>app:defaultDrawable="@drawable/foo"</code> both create new (distinct) Drawable instances,
     * therefore they are not equal.
     *
     * @return
     */
    public boolean isShowingDefaultDrawable() {
        if (mDefaultDrawable == null) {
            return false;
        }

        return mDefaultDrawable == getDrawable();
    }

    /**
     * Sets a drawable as the default drawable for the view.
     *
     * @param drawable The drawable to set
     * @see #getDefaultDrawable()
     */
    public void setDefaultDrawable(final Drawable drawable) {
        final boolean wasDefault = isShowingDefaultDrawable();

        if (mDefaultDrawable != null) {
            mDefaultDrawable.setCallback(null);
            unscheduleDrawable(mDefaultDrawable);
        }

        mDefaultDrawable = drawable;

        if (wasDefault || getDrawable() == null) {
            setImageDrawable(drawable);
        }
    }

    /**
     * Sets a resource id as the default drawable for the view.
     *
     * @param resId The desired resource identifier, as generated by the aapt tool. This integer encodes the package, type, and resource entry. The
     * value 0 is an invalid identifier.
     * @see #setDefaultDrawable(Drawable)
     * @attr ref com.myyearbook.m.R.styleable#DownloadableImageView_defaultDrawable
     */
    public void setDefaultResource(final int resId) {
        setDefaultDrawable(getResources().getDrawable(resId));
    }

    /**
     * Overrides the built-in ImageView onDraw() behavior, in order to intercept a recycled Bitmap, which otherwise causes a crash. If the Bitmap is
     * recycled, this performs a last-minute replacement using the default drawable.
     */
    @Override
    protected void onDraw(final Canvas canvas) {
        if (isDrawableRecycled()) {
            performAutoResetDefaultDrawable();
        }

        super.onDraw(canvas);
    }

    /**
     * Performs the automatic reset to the default drawable. This will also dispatch the
     * {@link DefaultDrawableListener#onAutoResetDefaultDrawable(ImageViewPlus)} callback.
     *
     * @see ImageViewPlus#resetToDefault()
     */
    protected void performAutoResetDefaultDrawable() {
        resetToDefault();

        if (mListener != null) {
            mListener.onAutoResetDefaultDrawable(this);
        }
    }

    @Override
    protected boolean setFrame(int l, int t, int r, int b) {
        if (PlusScaleType.TOP_CROP.equals(mScaleType)) {
            Drawable drawable = super.getDrawable();
            Matrix matrix = new Matrix();

            if (drawable != null) {
                float floatLeft = (float) l;
                float floatRight = (float) r;
                float intrinsicWidth = drawable.getIntrinsicWidth();

                if (intrinsicWidth != -1) {
                    float scaleFactor = (floatRight - floatLeft) / intrinsicWidth;
                    matrix.setScale(scaleFactor, scaleFactor);
                    setImageMatrix(matrix);
                }
            }
        }

        return super.setFrame(l, t, r, b);
    }

    /**
     * <code>
        <enum name="matrix" value="0" />
        <enum name="fitXY" value="1" />
        <enum name="fitStart" value="2" />
        <enum name="fitCenter" value="3" />
        <enum name="fitEnd" value="4" />
        <enum name="center" value="5" />
        <enum name="centerCrop" value="6" />
        <enum name="centerInside" value="7" />
        <enum name="top_crop" value="8" />
     * </code>
     *
     * @see ImageView.ScaleType
     * @see R.attr#scaleType
     */
    public static enum PlusScaleType {
        /**
         * Scale using the image matrix when drawing. The image matrix can be set using {@link ImageView#setImageMatrix(Matrix)}. From XML, use this
         * syntax: <code>android:scaleType="matrix"</code>.
         */
        MATRIX(ImageView.ScaleType.MATRIX, 0),
        /**
         * Scale the image using {@link Matrix.ScaleToFit#FILL}. From XML, use this syntax: <code>android:scaleType="fitXY"</code>.
         */
        FIT_XY(ImageView.ScaleType.FIT_XY, 1),
        /**
         * Scale the image using {@link Matrix.ScaleToFit#START}. From XML, use this syntax: <code>android:scaleType="fitStart"</code>.
         */
        FIT_START(ImageView.ScaleType.FIT_START, 2),
        /**
         * Scale the image using {@link Matrix.ScaleToFit#CENTER}. From XML, use this syntax: <code>android:scaleType="fitCenter"</code>.
         */
        FIT_CENTER(ImageView.ScaleType.FIT_CENTER, 3),
        /**
         * Scale the image using {@link Matrix.ScaleToFit#END}. From XML, use this syntax: <code>android:scaleType="fitEnd"</code>.
         */
        FIT_END(ImageView.ScaleType.FIT_END, 4),
        /**
         * Center the image in the view, but perform no scaling. From XML, use this syntax: <code>android:scaleType="center"</code>.
         */
        CENTER(ImageView.ScaleType.CENTER, 5),
        /**
         * Scale the image uniformly (maintain the image's aspect ratio) so that both dimensions (width and height) of the image will be equal to or
         * larger than the corresponding dimension of the view (minus padding). The image is then centered in the view. From XML, use this syntax:
         * <code>android:scaleType="centerCrop"</code>.
         */
        CENTER_CROP(ImageView.ScaleType.CENTER_CROP, 6),
        /**
         * Scale the image uniformly (maintain the image's aspect ratio) so that both dimensions (width and height) of the image will be equal to or
         * less than the corresponding dimension of the view (minus padding). The image is then centered in the view. From XML, use this syntax:
         * <code>android:scaleType="centerInside"</code>.
         */
        CENTER_INSIDE(ImageView.ScaleType.CENTER_INSIDE, 7),
        /**
         * Scale the image uniformly (maintain the image's aspect ratio) so that the width of the image fits the width of the ImageView, but the top
         * of the image is aligned with the top of the ImageView, cropping any excess from the bottom. From XML, use this syntax:
         * <code>android:scaleType="top_crop"</code>.
         */
        TOP_CROP(null, 8);

        public final ImageView.ScaleType superScaleType;
        public final int nativeInt;

        private final static SparseArray<PlusScaleType> sScaleTypes = new SparseArray<PlusScaleType>();

        static {
            for (PlusScaleType scaleType : values()) {
                sScaleTypes.put(scaleType.nativeInt, scaleType);
            }
        }

        public static PlusScaleType getScaleType(int nativeInt) {
            if (nativeInt >= 0) {
                return sScaleTypes.get(nativeInt);
            }

            return null;
        }

        private PlusScaleType(ImageView.ScaleType originType, int nativeInt) {
            this.superScaleType = originType;
            this.nativeInt = nativeInt;
        }
    }

    /**
     * Interface definition for callbacks to be invoked when ImageViewPlus events occur.
     */
    public interface DefaultDrawableListener {
        /**
         * Called when the view's Bitmap is found to be recycled JIT in onDraw(). The ImageView has already been reset back to the default view by
         * this point.
         *
         * @param view The view that had its drawable reset back to the default
         */
        public void onAutoResetDefaultDrawable(final ImageViewPlus view);
    }
}
