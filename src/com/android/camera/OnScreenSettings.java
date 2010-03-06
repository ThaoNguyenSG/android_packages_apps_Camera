/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.camera;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;

import com.google.android.camera.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * The on-screen setting menu.
 *
 * <p>Please reference to {@code android.widget.ZoomButtonsController} for
 * detail information about adding window to WindowManager.
 */
public class OnScreenSettings {
    @SuppressWarnings("unused")
    private static final String TAG = "OnScreenSettings";
    private static final int MSG_POST_SET_VISIBLE = 1;

    private static final Pattern TITLE_PATTERN =
            Pattern.compile("(.*)\\s*\\((.+)\\)");

    /**
     * A callback to be invoked when the on-screen menu's visibility changes.
     */
    public interface OnVisibilityChangedListener {
        public void onVisibilityChanged(boolean visibility);
    }

    private LayoutParams mContainerLayoutParams;
    private final Context mContext;
    private final Container mContainer;
    private final WindowManager mWindowManager;
    private final View mOwnerView;
    private ListView mMainMenu;
    private ListView mSubMenu;
    private boolean mIsVisible = false;
    private OnVisibilityChangedListener mVisibilityListener;
    private MainMenuAdapter mMainAdapter;

    private final LayoutInflater mInflater;
    private Runnable mRestoreRunner;
    private PreferenceGroup mPreferenceGroup;

    // We store the override values here. For a given preference,
    // if the mapping value of the preference key is not null, we will
    // use the value in this map instead of the value read from the preference
    //
    // This is design for the scene mode, for example, in the scene mode
    // "Action", the focus mode will become "infinite" no matter what in the
    // preference settings. So, we need to put a {pref_camera_focusmode_key,
    // "infinite"} entry in this map.
    private HashMap<String, String> mOverride = new HashMap<String, String>();

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_POST_SET_VISIBLE:
                    setVisible(true);
                    break;
            }
        }
    };

    public OnScreenSettings(View ownerView) {
        mContext = ownerView.getContext();
        mInflater = (LayoutInflater)
                mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        mWindowManager = (WindowManager)
                mContext.getSystemService(Context.WINDOW_SERVICE);
        mOwnerView = ownerView;
        mContainer = createContainer();
    }

    public void clearSettings() {
        SharedPreferences.Editor editor = PreferenceManager
                .getDefaultSharedPreferences(mContext).edit();
        editor.clear();
        editor.commit();

        mPreferenceGroup.reloadValue();
        if (mMainAdapter != null) {
            mMainAdapter.notifyDataSetChanged();
        }
        mOverride.clear();
    }

    public void setRestoreRunner(Runnable restorer) {
        mRestoreRunner = restorer;
    }

    public boolean isVisible() {
        return mIsVisible;
    }

    public void setOnVisibilityChangedListener(
            OnVisibilityChangedListener listener) {
        mVisibilityListener = listener;
    }

    public void setVisible(boolean visible) {
        mHandler.removeMessages(MSG_POST_SET_VISIBLE);
        if (visible) {
            if (mOwnerView.getWindowToken() == null) {
                /*
                 * We need a window token to show ourselves, maybe the owner's
                 * window hasn't been created yet but it will have been by the
                 * time the looper is idle, so post the setVisible(true) call.
                 */
                mHandler.sendEmptyMessage(MSG_POST_SET_VISIBLE);
                return;
            }
        }

        if (mIsVisible == visible) {
            return;
        }
        mIsVisible = visible;

        if (visible) {
            // Invalid the main adapter before show up, so it would be like
            // a new created list
            if (mMainAdapter != null) mMainAdapter.notifyDataSetInvalidated();
            if (mContainerLayoutParams.token == null) {
                mContainerLayoutParams.token = mOwnerView.getWindowToken();
            }
            mSubMenu.setVisibility(View.INVISIBLE);
            mMainMenu.setVisibility(View.VISIBLE);
            mWindowManager.addView(mContainer, mContainerLayoutParams);
            updateLayout();
        } else {
            // Reset the two menus

            mWindowManager.removeView(mContainer);
        }
        if (mVisibilityListener != null) {
            mVisibilityListener.onVisibilityChanged(mIsVisible);
        }
    }

    // Override the preference settings, if value == null, then disable the
    // override.
    public void overrideSettings(String key, String value) {
        if (value == null) {
            if (mOverride.remove(key) != null && mMainAdapter != null) {
                mMainAdapter.notifyDataSetChanged();
            }
        } else {
            if (mOverride.put(key, value) == null && mMainAdapter != null) {
                mMainAdapter.notifyDataSetChanged();
            }
        }
    }

    public void updateLayout() {
        // if the mOwnerView is detached from window then skip.
        if (mOwnerView.getWindowToken() == null) return;
        Display display = mWindowManager.getDefaultDisplay();

        mContainerLayoutParams.x = 0;
        mContainerLayoutParams.y = 0;

        mContainerLayoutParams.width = display.getWidth() / 2;
        mContainerLayoutParams.height = display.getHeight();

        if (mIsVisible) {
            mWindowManager.updateViewLayout(mContainer, mContainerLayoutParams);
        }
    }

    private void showSubMenu() {
        Util.slideOut(mMainMenu, Util.DIRECTION_LEFT);
        Util.slideIn(mSubMenu, Util.DIRECTION_RIGHT);
        mSubMenu.requestFocus();
    }

    private void closeSubMenu() {
        Util.slideOut(mSubMenu, Util.DIRECTION_RIGHT);
        Util.slideIn(mMainMenu, Util.DIRECTION_LEFT);
    }

    private Container createContainer() {
        LayoutParams lp = new LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        lp.flags = LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
        lp.gravity = Gravity.TOP | Gravity.LEFT;
        lp.height = LayoutParams.WRAP_CONTENT;
        lp.width = LayoutParams.WRAP_CONTENT;
        lp.type = LayoutParams.TYPE_APPLICATION_PANEL;
        lp.format = PixelFormat.OPAQUE;
        lp.windowAnimations = R.style.Animation_OnScreenMenu;

        mContainerLayoutParams = lp;

        Container container = new Container(mContext);
        container.setLayoutParams(lp);

        mInflater.inflate(R.layout.on_screen_menu, container);

        mMainMenu = (ListView) container.findViewById(R.id.menu_view);
        mSubMenu = (ListView) container.findViewById(R.id.sub_menu);

        container.findViewById(R.id.btn_gripper)
                .setOnTouchListener(new GripperTouchListener());

        return container;
    }

    private class GripperTouchListener implements View.OnTouchListener {
        public boolean onTouch(View view, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    return true;
                case MotionEvent.ACTION_UP:
                    setVisible(false);
                    return true;
            }
            return false;
        }
    }

    private boolean onContainerKey(KeyEvent event) {
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_CAMERA:
            case KeyEvent.KEYCODE_FOCUS:
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_MENU:
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    setVisible(false);
                    return true;
                }
        }
        return false;
    }

    // Add the preference and it's children recursively to the given list. So
    // that we can show the preference (and it's children) in the list view.
    private static void addPreference(
            CameraPreference preference, ArrayList<CameraPreference> list) {
        list.add(preference);
        if (preference instanceof PreferenceGroup) {
            PreferenceGroup group = (PreferenceGroup) preference;
            for (int i = 0, n = group.size(); i < n; ++i) {
                CameraPreference child = group.get(i);
                addPreference(child, list);
            }
        }
    }

    public void setPreferenceGroup(PreferenceGroup group) {
        this.mPreferenceGroup = group;

        ArrayList<CameraPreference> list = new ArrayList<CameraPreference>();

        // We don't want the root group added to the list. So, we add the
        // children of the root here
        for (int  i = 0, n = group.size(); i < n; ++i) {
            addPreference(group.get(i), list);
        }
        mMainAdapter = new MainMenuAdapter(mContext, list);
        mMainMenu.setAdapter(mMainAdapter);
        mMainMenu.setOnItemClickListener(mMainAdapter);
    }

    private View inflateIfNeed(
            View view, int resource, ViewGroup root, boolean attachToRoot) {
        if (view != null) return view;
        return mInflater.inflate(resource, root, attachToRoot);
    }

    private class MainMenuAdapter extends BaseAdapter
            implements OnItemClickListener {
        private final ArrayList<CameraPreference> mPreferences;

        public MainMenuAdapter(
                Context context, ArrayList<CameraPreference> preferences) {
            mPreferences = preferences;
        }

        public void onItemClick(
                AdapterView<?> parent, View view, int position, long id) {
            // If not the last item (restore settings)
            if (position < mPreferences.size()) {
                CameraPreference preference = mPreferences.get(position);
                SubMenuAdapter adapter = new SubMenuAdapter(
                        mContext, (ListPreference) preference);
                mSubMenu.setAdapter(adapter);
                mSubMenu.setOnItemClickListener(adapter);
                showSubMenu();
            } else {
                MenuHelper.confirmAction(mContext,
                        mContext.getString(R.string.confirm_restore_title),
                        mContext.getString(R.string.confirm_restore_message),
                        mRestoreRunner);
            }
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            // The last item in the view (i.e., restore settings)
            if (position == mPreferences.size()) {
                convertView = inflateIfNeed(convertView,
                        R.layout.on_screen_menu_list_item, parent, false);

                ((TextView) convertView.findViewById(R.id.title)).setText(
                        mContext.getString(R.string.pref_restore_title));
                ((TextView) convertView.findViewById(R.id.summary)).setText(
                        mContext.getString(R.string.pref_restore_detail));

                // Make sure we didn't set focusable. Otherwise, it will
                // consume all the events. See more detail in the comments
                // below.
                convertView.setFocusable(false);
                return convertView;
            }

            CameraPreference preference = mPreferences.get(position);
            if (preference instanceof PreferenceGroup) {
                convertView = inflateIfNeed(convertView,
                        R.layout.on_screen_menu_header, parent, false);
                PreferenceGroup group = (PreferenceGroup) preference;
                ((TextView) convertView.findViewById(
                        R.id.title)).setText(group.getTitle());
            } else {
                convertView = inflateIfNeed(convertView,
                        R.layout.on_screen_menu_list_item, parent, false);

                String override = mOverride.get(
                        ((ListPreference) preference).getKey());
                TextView title = (TextView)
                        convertView.findViewById(R.id.title);
                title.setText(preference.getTitle());
                title.setEnabled(override == null);

                TextView summary = (TextView)
                        convertView.findViewById(R.id.summary);
                summary.setText(override == null
                        ? ((ListPreference) preference).getEntry()
                        : override);
                summary.setEnabled(override == null);

                // A little trick here, making the view focusable will eat
                // both touch/key events on the view and thus make it looks
                // like disabled.
                convertView.setFocusable(override != null);
            }
            return convertView;
        }

        @Override
        public boolean areAllItemsEnabled() {
            return false;
        }

        @Override
        public boolean isEnabled(int position) {
            // The last item (restore default)
            if (position == mPreferences.size()) return true;

            CameraPreference preference = mPreferences.get(position);
            return !(preference instanceof PreferenceGroup);
        }

        public int getCount() {
            // The last one is "restore default"
            return mPreferences.size() + 1;
        }

        public Object getItem(int position) {
            return null;
        }

        public long getItemId(int position) {
            return position;
        }

        @Override
        public int getItemViewType(int position) {
            if (position == mPreferences.size()) return 1;
            CameraPreference pref = mPreferences.get(position);
            if (pref instanceof PreferenceGroup) return 0;
            if (pref instanceof ListPreference) return 1;
            throw new IllegalStateException();
        }

        @Override
        public int getViewTypeCount() {
            // we have two types, see getItemViewType()
            return 2;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        @Override
        public boolean isEmpty() {
            return mPreferences.isEmpty();
        }
    }

    private class SubMenuAdapter extends BaseAdapter
            implements OnItemClickListener {
        private final ListPreference mPreference;
        private final IconListPreference mIconPreference;

        public SubMenuAdapter(Context context, ListPreference preference) {
            mPreference = preference;
            mIconPreference = (preference instanceof IconListPreference)
                    ? (IconListPreference) preference
                    : null;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            CharSequence entry[] = mPreference.getEntries();
            if (position == 0) {
                convertView = inflateIfNeed(convertView,
                        R.layout.on_screen_menu_header, parent, false);
                ((TextView) convertView.findViewById(
                        R.id.title)).setText(mPreference.getTitle());
            } else {
                int index = position - 1;
                convertView = inflateIfNeed(convertView,
                        R.layout.on_screen_submenu_item, parent, false);
                boolean checked = mPreference.getValue().equals(
                        mPreference.getEntryValues()[index]);
                String title = entry[index].toString();
                String detail = null;

                // Handle the title of format "Title (details)". We extract the
                // details from the title message for better UI layout. The
                // detail will be shown in second line with a smaller font.
                Matcher matcher = TITLE_PATTERN.matcher(title);
                if (matcher.matches()) {
                    title = matcher.group(1);
                    detail = matcher.group(2);
                }

                ((TextView) convertView
                        .findViewById(R.id.title)).setText(title);
                TextView detailView = (TextView)
                        convertView.findViewById(R.id.summary);
                if (detail == null) {
                    detailView.setVisibility(View.GONE);
                } else {
                    detailView.setVisibility(View.VISIBLE);
                    detailView.setText(detail);
                }

                ((RadioButton) convertView.findViewById(
                        R.id.radio_button)).setChecked(checked);
                ImageView icon = (ImageView)
                        convertView.findViewById(R.id.icon);
                if (mIconPreference != null) {
                    icon.setVisibility(View.VISIBLE);
                    icon.setImageDrawable(
                            mIconPreference.getIcons()[position - 1]);
                } else {
                    icon.setVisibility(View.GONE);
                }
            }
            return convertView;
        }

        @Override
        public boolean areAllItemsEnabled() {
            return false;
        }

        @Override
        public boolean isEnabled(int position) {
            return getItemViewType(position) != 0;
        }

        public int getCount() {
            // add one for the header
            return mPreference.getEntries().length + 1;
        }

        public Object getItem(int position) {
            return null;
        }

        public long getItemId(int position) {
            return position;
        }

        @Override
        public int getItemViewType(int position) {
            return position == 0 ? 0 : 1;
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        public void onItemClick(
                AdapterView<?> parent, View view, int position, long id) {
            CharSequence values[] = mPreference.getEntryValues();
            int idx = mPreference.findIndexOfValue(mPreference.getValue());
            if (idx != position - 1) {
                mPreference.setValueIndex(position - 1);
                notifyDataSetChanged();
                mMainAdapter.notifyDataSetChanged();
                return;
            }

            // Close the sub menu when user presses the original option.
            closeSubMenu();
        }
    }

    private class Container extends FrameLayout {
        public Container(Context context) {
            super(context);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (super.onTouchEvent(event)) return true;
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                setVisible(false);
                return true;
            }
            return false;
        }

        /*
         * Need to override this to intercept the key events. Otherwise, we
         * would attach a key listener to the container but its superclass
         * ViewGroup gives it to the focused View instead of calling the key
         * listener, and so we wouldn't get the events.
         */
        @Override
        public boolean dispatchKeyEvent(KeyEvent event) {
            return onContainerKey(event)
                    ? true
                    : super.dispatchKeyEvent(event);
        }
    }
}
