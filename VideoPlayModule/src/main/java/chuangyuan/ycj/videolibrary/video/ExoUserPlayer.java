
package chuangyuan.ycj.videolibrary.video;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.support.annotation.IdRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;

import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import chuangyuan.ycj.videolibrary.listener.DataSourceListener;
import chuangyuan.ycj.videolibrary.listener.ExoPlayerListener;
import chuangyuan.ycj.videolibrary.listener.ExoPlayerViewListener;
import chuangyuan.ycj.videolibrary.listener.ItemVideo;
import chuangyuan.ycj.videolibrary.listener.VideoInfoListener;
import chuangyuan.ycj.videolibrary.utils.VideoPlayUtils;
import chuangyuan.ycj.videolibrary.widget.VideoPlayerView;

public class ExoUserPlayer {

    private static final String TAG = ExoUserPlayer.class.getName();
    /*** 获取网速大小 ***/
    private long lastTotalRxBytes = 0;
    /*** 获取最后的时间戳 ***/
    private long lastTimeStamp = 0;
    /*** 获取当前进度 ***/
    private long resumePosition;
    /*** 获取当前视频源位置 ***/
    private int resumeWindow;
    /*** 定时任务类 ***/
    private Timer timer;
    /*** 播放view ***/
    private VideoPlayerView mPlayerView;
    /*** 网络状态监听***/
    private NetworkBroadcastReceiver mNetworkBroadcastReceiver;
    /***  视频状态回调接口 ***/
    private ComponentListener componentListener;
    /*** view交互回调接口 ***/
    private PlayComponentListener playComponentListener;
    /*** 视频回调信息接口 ***/
    private VideoInfoListener videoInfoListener;
    /*** 播放view交互接口 ***/
    ExoPlayerViewListener mPlayerViewListener;
    /*** 数据来源状态***/
    private boolean playerNeedsSource;
    /*** 数据来源状态***/
    /*** 是否循环播放  0 不开启***/
    private int loopingCount = 0;
    /***当前活动**/
    Activity activity;
    /*** 内核播放控制**/
    SimpleExoPlayer player;
    /***数据源管理类**/
    MediaSourceBuilder mediaSourceBuilder;
    /*** 屏蔽进度缩索引 ***/
    int indexType = -1;//设置
    List<String> videoUri;
    List<String> nameUri;
    /*** 是否手动暂停 ***/
    Boolean handPause = false;//
    /*** 是否已经在只停止恢复 ***/
    Boolean isPause = false;//

    /****
     * 初始化
     *
     * @param activity   活动对象
     * @param playerView 播放控件
     **/
    public ExoUserPlayer(@NonNull Activity activity, @NonNull VideoPlayerView playerView) {
        this(activity, playerView, null);
    }

    /****
     * @param activity 活动对象
     * @param reId     播放控件id
     **/
    public ExoUserPlayer(@NonNull Activity activity, @IdRes int reId) {
        this(activity, reId, null);
    }

    /****
     * 初始化
     *
     * @param activity 活动对象
     * @param reId     播放控件id
     * @param listener 自定义数据源类
     **/
    public ExoUserPlayer(@NonNull Activity activity, @IdRes int reId, @Nullable DataSourceListener listener) {
        this(activity, (VideoPlayerView) activity.findViewById(reId), listener);
    }

    /****
     * 初始化
     *
     * @param activity   活动对象
     * @param playerView 播放控件
     * @param listener   自定义数据源类
     **/
    public ExoUserPlayer(@NonNull Activity activity, @NonNull VideoPlayerView playerView, @Nullable DataSourceListener listener) {
        this.activity = activity;
        this.mPlayerView = playerView;
        mediaSourceBuilder = new MediaSourceBuilder(listener);
        initView();
    }

    private void initView() {
        playComponentListener = new PlayComponentListener();
        componentListener = new ComponentListener();
        mPlayerView.setExoPlayerListener(playComponentListener);
        mPlayerViewListener = mPlayerView.getComponentListener();
        timer = new Timer();
        timer.schedule(task, 0, 1000); // 1s后启动任务，每1s执行一次
    }

    /***
     * 设置进度
     *
     * @param resumePosition 毫秒
     **/
    public void setPosition(long resumePosition) {
        this.resumePosition = resumePosition;
    }

    /***
     *   隐藏进度条
     **/
    public void hideSeekBar() {
        Assertions.checkArgument(mPlayerViewListener != null);
        mPlayerViewListener.showHidePro(View.INVISIBLE);
    }

    /***
     * 显示隐藏进度条
     **/
    public void showSeekBar() {
        Assertions.checkArgument(mPlayerViewListener != null);
        mPlayerViewListener.showHidePro(View.VISIBLE);
    }

    /***
     * 隐藏控制布局
     * ***/
    public void hideControllerView() {
        assert mPlayerViewListener != null;
        mPlayerViewListener.hideController();
    }

    /***
     * 设置循环播放视频
     *
     * @param loopingCount  必须大于0
     **/
    public void setLooping(int loopingCount) {
        Assertions.checkArgument(loopingCount > 0);
        this.loopingCount = loopingCount;
    }

    public void hideController() {
        Assertions.checkArgument(mPlayerViewListener != null);
        mPlayerViewListener.hideController();
    }

    /***
     * 是否播放中
     * @return boolean
     * ***/
    public boolean isPlaying() {
        Assertions.checkArgument(player != null);
        return player.getPlayWhenReady();
    }

    /**
     * 设置播放路径
     *
     * @param uri 路径
     ***/
    public void setPlayUri(@NonNull String uri) {
        setPlayUri(Uri.parse(uri));
    }

    /****
     * @param firstVideoUri  预览的视频
     * @param secondVideoUri 第二个视频
     * @param  indexType 设置当前索引视频屏蔽进度
     **/
    public void setPlayUri(@NonNull String firstVideoUri, @NonNull String secondVideoUri, int indexType) {
        setPlayUri(Uri.parse(firstVideoUri), Uri.parse(secondVideoUri), indexType);
    }

    /****
     * @param firstVideoUri  预览的视频
     * @param secondVideoUri 第二个视频
     **/
    public void setPlayUri(@NonNull Uri firstVideoUri, @NonNull Uri secondVideoUri) {
        setPlayUri(firstVideoUri, secondVideoUri, 0);
    }

    /****
     * @param firstVideoUri  预览的视频
     * @param secondVideoUri 第二个视频
     * @param  indexType  设置当前索引视频屏蔽进度
     **/
    public void setPlayUri(@NonNull Uri firstVideoUri, @NonNull Uri secondVideoUri, int indexType) {
        if (mediaSourceBuilder != null) {
            mediaSourceBuilder.release();
        }

        this.indexType = indexType;
        mediaSourceBuilder.setMediaSourceUri(activity.getApplicationContext(), firstVideoUri, secondVideoUri);
        createPlayers();
        registerReceiverNet();
    }

    /****
     * 设置视频列表播放
     * @param uris  视频列表集合
     **/
    public void setPlayUri(@NonNull String... uris) {
        Uri[] s = new Uri[uris.length];
        for (int i = 0; i < uris.length; i++) {
            s[i] = Uri.parse(uris[i]);
        }
        setPlayUri(s);
    }

    /****
     * 设置视频列表播放
     * @param uris  视频列表集合
     **/
    public void setPlayUri(@NonNull Uri... uris) {
        if (mediaSourceBuilder != null) {
            mediaSourceBuilder.release();
        }
        mediaSourceBuilder.setMediaSourceUri(activity.getApplicationContext(), uris);
        createPlayers();
        registerReceiverNet();
    }

    /****
     * 设置视频列表播放
     * @param uris  视频列表集合
     **/
    public void setPlayUri(@NonNull List<ItemVideo> uris) {
        if (mediaSourceBuilder != null) {
            mediaSourceBuilder.release();
        }
        mediaSourceBuilder.setMediaSourceUri(activity.getApplicationContext(), uris);
        createPlayers();
        registerReceiverNet();
    }

    /**
     * 设置多线路播放
     *
     * @param videoUri 视频地址
     * @param name     清清晰度显示名称
     **/
    public void setPlaySwitchUri(@NonNull String[] videoUri, @NonNull String[] name) {
        setPlaySwitchUri(Arrays.asList(videoUri), Arrays.asList(name));
    }

    /**
     * 设置多线路播放
     *
     * @param videoUri 视频地址
     * @param name     清清晰度显示名称
     * @param index    选中播放线路
     **/
    public void setPlaySwitchUri(@NonNull String[] videoUri, @NonNull String[] name, int index) {
        setPlaySwitchUri(Arrays.asList(videoUri), Arrays.asList(name), index);
    }

    /**
     * 设置多线路播放
     *
     * @param videoUri 视频地址
     * @param name     清清晰度显示名称
     **/
    public void setPlaySwitchUri(@NonNull List<String> videoUri, @NonNull List<String> name) {
        setPlaySwitchUri(videoUri, name, 0);
    }

    /**
     * 设置多线路播放
     *
     * @param videoUri 视频地址
     * @param name     清清晰度显示名称
     * @param index    选中播放线路
     **/
    public void setPlaySwitchUri(@NonNull List<String> videoUri, @NonNull List<String> name, int index) {
        this.videoUri = videoUri;
        this.nameUri = name;
        mPlayerViewListener.showSwitchName(nameUri.get(index));
        if (mediaSourceBuilder != null) {
            mediaSourceBuilder.release();
        }
        mediaSourceBuilder.setMediaSourceUri(activity.getApplicationContext(), Uri.parse(videoUri.get(index)));
        createPlayers();
        registerReceiverNet();
    }


    /**
     * 设置播放路径
     *
     * @param uri 路径
     ***/
    public void setPlayUri(@NonNull Uri uri) {
        if (mediaSourceBuilder != null) {
            mediaSourceBuilder.release();
        }
        mediaSourceBuilder.setMediaSourceUri(activity.getApplicationContext(), uri);
        createPlayers();
        registerReceiverNet();
    }

    /***
     * 页面恢复处理
     **/
    public void onResume() {
        if ((Util.SDK_INT <= 23 || player == null)) {
            createPlayers();
        }
    }

    /***
     * 页面暂停处理
     **/
    public void onPause() {
        isPause = true;
        if (player != null) {
            handPause = !player.getPlayWhenReady();
            releasePlayers();
        }
    }

    /**
     * 页面销毁处理
     **/
    public void onDestroy() {
        releasePlayers();
        if (mPlayerView != null) {
            mPlayerView.setExoPlayerListener(null);
            mPlayerView.onDestroy();
            mPlayerView = null;
        }
    }

    /***
     * 释放资源
     **/
    public void releasePlayers() {
        if (player != null) {
            updateResumePosition();
            unNetworkBroadcastReceiver();
            player.release();
            player.removeListener(componentListener);
            player = null;
        }
        if (mediaSourceBuilder != null) {
            mediaSourceBuilder.release();
        }
        if (activity.isFinishing()) {
            isPause = null;
            handPause = null;
            indexType = -1;
            nameUri = null;
            videoUri = null;
            if (timer != null) {
                timer.cancel();
                timer = null;
            }
            if (task != null) {
                task.cancel();
                task = null;
            }
            mediaSourceBuilder = null;
            playComponentListener = null;
            componentListener = null;
            mPlayerViewListener = null;
            videoInfoListener = null;
        }
    }

    /****
     * 创建
     **/
    void createPlayers() {
        if (player == null) {
            player = createFullPlayer();
            playerNeedsSource = true;
        }
        playVideo();

    }

    /****
     * 创建
     **/
    void createPlayersNo() {
        if (player == null) {
            player = createFullPlayer();
            playerNeedsSource = true;
        }
    }

    /****
     * 创建
     **/
    void createPlayersPlay() {
        player = createFullPlayer();
    }

    private SimpleExoPlayer createFullPlayer() {
        TrackSelection.Factory videoTrackSelectionFactory = new AdaptiveTrackSelection.Factory(new DefaultBandwidthMeter());
        TrackSelector trackSelector = new DefaultTrackSelector(videoTrackSelectionFactory);
        SimpleExoPlayer player = ExoPlayerFactory.newSimpleInstance(activity, trackSelector);
        mPlayerView.setPlayer(player);
        return player;
    }

    /***
     * 播放视频
     **/
    private void playVideo() {
        if (VideoPlayUtils.isWifi(activity)) {
            onPlayNoAlertVideo();
        } else {
            if (isPause) {
                onPlayNoAlertVideo();
            } else {
                mPlayerViewListener.showAlertDialog();
            }
        }
    }

    /***
     * 播放视频
     **/

    protected void onPlayNoAlertVideo() {
        if (player == null) {
            createPlayersPlay();
        }
        boolean haveResumePosition = resumeWindow != C.INDEX_UNSET;
        if (haveResumePosition) {
            player.seekTo(resumeWindow, resumePosition);
        }
        if (handPause) {
            player.setPlayWhenReady(false);
        } else {
            player.setPlayWhenReady(true);
        }
        if (loopingCount == 0) {
            player.prepare(mediaSourceBuilder.getMediaSource(), !haveResumePosition, true);
        } else {
            player.prepare(mediaSourceBuilder.setLooping(loopingCount), !haveResumePosition, true);
        }
        player.addListener(componentListener);
        playerNeedsSource = false;
    }

    /****
     * 重置进度
     **/
    protected void updateResumePosition() {
        if (player != null) {
            resumeWindow = player.getCurrentWindowIndex();
            resumePosition = player.isCurrentWindowSeekable() ? Math.max(0, player.getCurrentPosition())
                    : C.TIME_UNSET;
        }
    }

    /**
     * 清除进度
     ***/
    private void clearResumePosition() {
        resumeWindow = C.INDEX_UNSET;
        resumePosition = C.TIME_UNSET;
    }

    /***
     * 网络变化任务
     **/
    private TimerTask task = new TimerTask() {
        @Override
        public void run() {
            if (mPlayerView.isLoadingLayoutShow()) {
                mPlayerViewListener.showNetSpeed(getNetSpeed());
            }
        }
    };

    /****
     * 获取当前网速
     *
     * @return String 二返回当前网速字符
     **/
    private String getNetSpeed() {
        String netSpeed;
        long nowTotalRxBytes = VideoPlayUtils.getTotalRxBytes(activity);
        long nowTimeStamp = System.currentTimeMillis();
        long calculationTime = (nowTimeStamp - lastTimeStamp);
        if (calculationTime == 0) {
            netSpeed = String.valueOf(1) + " kb/s";
            return netSpeed;
        }
        long speed = ((nowTotalRxBytes - lastTotalRxBytes) * 1000 / calculationTime);//毫秒转换
        lastTimeStamp = nowTimeStamp;
        lastTotalRxBytes = nowTotalRxBytes;
        if (speed > 1024) {
            DecimalFormat df = new DecimalFormat("######0.0");
            netSpeed = String.valueOf(df.format(VideoPlayUtils.getM(speed))) + " MB/s";
        } else {
            netSpeed = String.valueOf(speed) + " kb/s";
        }
        return netSpeed;
    }

    /****
     * 监听返回键 true 可以正常返回处理，false 切换到竖屏
     *
     * @return boolean
     ***/
    public boolean onBackPressed() {
        if (activity.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            mPlayerView.exitFullView();
            return false;
        } else {
            return true;
        }
    }

    public VideoPlayerView getPlayerView() {
        return mPlayerView;
    }

    /****
     * 横竖屏切换
     *
     * @param configuration 旋转
     ***/
    public void onConfigurationChanged(Configuration configuration) {
        mPlayerViewListener.onConfigurationChanged(configuration.orientation);
    }

    /***
     * 显示水印图
     *
     * @param res 资源
     ***/
    public void setExoPlayWatermarkImg(int res) {
        mPlayerViewListener.setWatermarkImage(res);
    }

    public void setTitle(@NonNull String title) {
        mPlayerViewListener.setTitle(title);
    }

    public void setVideoInfoListener(VideoInfoListener videoInfoListener) {
        this.videoInfoListener = videoInfoListener;
    }

    /***
     * 设置播放或暂停
     * @param value  true 播放  false  暂停
     * **/
    public void setStartOrPause(boolean value) {
        if (player != null) {
            player.setPlayWhenReady(value);
        }
    }

    /***
     * 设置显示多线路图标
     * @param showVideoSwitch true 显示 false 不显示
     * **/
    public void setShowVideoSwitch(boolean showVideoSwitch) {
        mPlayerViewListener.setShowWitch(showVideoSwitch);
    }

    /***
     * 设置显示多线路图标
     * @param isOpenSeek true 启用 false 不启用
     * **/
    public void setSeekBarSeek(boolean isOpenSeek) {
        mPlayerViewListener.setSeekBarOpenSeek(isOpenSeek);
    }

    /***
     * 获取内核播放实例
     * @return SimpleExoPlayer
     * ****/
    public SimpleExoPlayer getPlayer() {
        return player;
    }

    /**
     * 返回视频总进度  以毫秒为单位
     *
     * @return long
     **/
    public long getDuration() {
        return player == null ? 0 : player.getDuration();
    }

    /**
     * 返回视频当前播放进度  以毫秒为单位
     *
     * @return long
     **/
    public long getCurrentPosition() {
        return player == null ? 0 : player.getCurrentPosition();
    }

    /**
     * 返回视频当前播放d缓冲进度  以毫秒为单位
     *
     * @return long
     **/
    public long getBufferedPosition() {
        return player == null ? 0 : player.getBufferedPosition();
    }

    /***
     * 注册广播监听
     **/
    protected void registerReceiverNet() {
        if (mNetworkBroadcastReceiver == null) {
            IntentFilter intentFilter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
            mNetworkBroadcastReceiver = new NetworkBroadcastReceiver();
            activity.registerReceiver(mNetworkBroadcastReceiver, intentFilter);
        }
    }

    /***
     * 取消广播监听
     **/
    private void unNetworkBroadcastReceiver() {
        if (mNetworkBroadcastReceiver != null) {
            activity.unregisterReceiver(mNetworkBroadcastReceiver);
        }
        mNetworkBroadcastReceiver = null;
    }

    /***
     * 网络监听类
     ***/
    private class NetworkBroadcastReceiver extends BroadcastReceiver {
        long is = 0;

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
                ConnectivityManager mConnectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkInfo netInfo = mConnectivityManager.getActiveNetworkInfo();
                if (netInfo != null && netInfo.isAvailable()) {
                    /////////////网络连接
                    if (netInfo.getType() == ConnectivityManager.TYPE_WIFI) {
                        /////WiFi网络
                    } else if (netInfo.getType() == ConnectivityManager.TYPE_MOBILE) {
                        /////////3g网络
                        if (System.currentTimeMillis() - is > 500) {
                            is = System.currentTimeMillis();
                            if (!isPause) {
                                mPlayerViewListener.showAlertDialog();
                            }
                        }
                    }
                    Log.d(TAG, "onReceive:" + netInfo.getType() + "__:");
                }
            }

        }
    }

    /****
     * 播放坚挺回调事件处理
     * ***/
    private class PlayComponentListener implements ExoPlayerListener {
        @Override
        public void onCreatePlayers() {
            createPlayers();
        }

        @Override
        public void onClearPosition() {
            clearResumePosition();

        }

        @Override
        public void replayPlayers() {
            clearResumePosition();
            onPlayNoAlertVideo();
        }

        @Override
        public void switchUri(int position, String name) {
            if (mediaSourceBuilder != null) {
                mediaSourceBuilder.release();
            }
            mediaSourceBuilder.setMediaSourceUri(activity.getApplicationContext(), Uri.parse(videoUri.get(position)));
            updateResumePosition();
            onPlayNoAlertVideo();
        }

        @Override
        public void playVideoUri() {
            onPlayNoAlertVideo();

        }

        @Override
        public ExoUserPlayer getPlay() {
            return ExoUserPlayer.this;
        }

        @Override
        public void onDetachedFromWindow() {

        }


        @Override
        public void onBack() {
            if (activity != null) {
                activity.onBackPressed();
            }
        }
    }

    private class ComponentListener implements Player.EventListener {
        /***
         * 视频播放播放
         **/
        @Override
        public void onTimelineChanged(Timeline timeline, Object manifest) {

        }

        @Override
        public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
            Log.d(TAG, "onTracksChanged:" + trackGroups.length + "_:" + trackSelections.length);
            //  Log.d(TAG, "onTracksChanged:Timeline:" + player.getCurrentWindowIndex());
            //   int nextWindowIndex = player.getCurrentTimeline().getNextWindowIndex(player.getCurrentWindowIndex(), player.getRepeatMode());
            //   Log.d(TAG, "onTracksChanged:nextWindowIndex:" + nextWindowIndex);
            if (trackSelections.length > 1 && indexType > -1) {
                if (player.getCurrentWindowIndex() == indexType) {
                    mPlayerView.getTimeBar().setOpenSeek(false);
                } else {
                    mPlayerView.getTimeBar().setOpenSeek(true);
                }
            }
        }

        /*****
         * 进度条控制 加载页
         *********/
        @Override
        public void onLoadingChanged(boolean isLoading) {
            Log.d(TAG, "onLoadingChanged:" + isLoading + "" + player.getPlayWhenReady());
        }

        /**
         * 视频的播放状态
         * STATE_IDLE 播放器空闲，既不在准备也不在播放
         * STATE_PREPARING 播放器正在准备
         * STATE_BUFFERING 播放器已经准备完毕，但无法立即播放。此状态的原因有很多，但常见的是播放器需要缓冲更多数据才能开始播放
         * STATE_PAUSE 播放器准备好并可以立即播放当前位置
         * STATE_PLAY 播放器正在播放中
         * STATE_ENDED 播放已完毕
         */
        @Override
        public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
            if (playWhenReady) {
                activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);//防锁屏

            } else {
                activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);//防锁屏
            }
            Log.d(TAG, "onPlayerStateChanged:+playWhenReady:" + playWhenReady);
            switch (playbackState) {
                case Player.STATE_BUFFERING:
                    Log.d(TAG, "onPlayerStateChanged:加载中。。。");
                    if (playWhenReady) {
                        mPlayerViewListener.showLoadStateView(View.VISIBLE);
                    }
                    if (videoInfoListener != null) {
                        videoInfoListener.onLoadingChanged();
                    }
                    break;
                case Player.STATE_ENDED:
                    Log.d(TAG, "onPlayerStateChanged:ended。。。");
                    mPlayerViewListener.showReplayView(View.VISIBLE);
                    if (videoInfoListener != null) {
                        videoInfoListener.onPlayEnd();
                    }
                    break;
                case Player.STATE_IDLE://空的
                    Log.d(TAG, "onPlayerStateChanged::网络状态差，请检查网络。。。");
                    updateResumePosition();
                    if (!VideoPlayUtils.isNetworkAvailable(activity)) {
                        if (playerNeedsSource) {
                            mPlayerViewListener.showErrorStateView(View.VISIBLE);
                        }
                    } else {
                        mPlayerViewListener.showErrorStateView(View.VISIBLE);
                    }
                    break;
                case Player.STATE_READY:
                    Log.d(TAG, "onPlayerStateChanged:ready。。。");
                    mPlayerViewListener.showLoadStateView(View.GONE);
                    if (videoInfoListener != null) {
                        isPause = false;
                        videoInfoListener.onPlayStart();
                    }
                    break;
                default:
                    break;
            }
        }

        @Override
        public void onRepeatModeChanged(int repeatMode) {
            if (videoInfoListener != null) {
                videoInfoListener.onRepeatModeChanged(repeatMode);
            }

        }

        @Override
        public void onPlayerError(ExoPlaybackException e) {
            Log.e(TAG, "onPlayerError:" + e.getMessage());
            playerNeedsSource = true;
            if (VideoPlayUtils.isBehindLiveWindow(e)) {
                clearResumePosition();
                playVideo();
            } else {
                mPlayerViewListener.showErrorStateView(View.VISIBLE);
                if (videoInfoListener != null) {
                    videoInfoListener.onPlayerError(e);
                }
            }
        }

        @Override
        public void onPositionDiscontinuity() {
            Log.d(TAG, "onPositionDiscontinuity:");
            if (playerNeedsSource) {
                updateResumePosition();
            }
        }

        @Override
        public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {

        }
    }
}

