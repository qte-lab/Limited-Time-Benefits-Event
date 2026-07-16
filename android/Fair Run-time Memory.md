公平运行内存适配
1. 概述
近年来随着市场发展，应用功能持续叠加，运行软件所需要的内存大小随之提升。然而在终端设备中，内存资源大小有限，内存占用过高会导致系统低内存、频繁内存回收等问题。在用户侧，这些会被感知为卡顿、发热、应用重新加载甚至应用闪退，导致应用在市场竞争中失去用户信任。此外，高内存应用运行时挤占系统内存空间，导致其他应用无法驻留。


上述问题的根因在于应用内存的使用缺乏统一约束与可预期边界，导致系统整体资源分配的不确定性与失衡。一方面，用户期望设备在多应用并行场景下依然保持流畅、稳定且响应及时；另一方面，开发者也需要一个明确、统一的内存使用标准，以指导设计与优化，避免因不可控的内存行为影响应用表现。因此，系统亟需构建一套内存治理机制，在保障单应用体验的同时，实现多应用之间的资源公平分配。在此背景下，引入公平运行内存机制，通过对应用内存使用建立边界与约束，实现系统资源的有序调度与整体体验的稳定提升。

2. 公平运行内存机制介绍
2.1 应用内存指标体系
2.1.1 高优先级进程
什么是高优先级进程：在应用运行过程中，保证应用正常运行时所需的进程。例如IM类应用前台运行时，应用主进程和相应的推送进程是高优进程。

如何判定一个进程是否为高优先级，以下进程视为高优先级进程：

1）进程 oom_score_adj <= 200；

2）应用处于后台时，UI进程视为高优进程；

3）与满足前两个条件的进程有绑定关系的进程；

4）系统低内存时被查杀后仍然频繁拉起的进程；

2.1.2 进程的内存指标
在 Android 系统中，通常会遇到以下四种统计指标（VSS、RSS、PSS、USS）。

含义	特点
VSS	进程能够访问的所有虚拟内存地址空间总和	包含了已分配但尚未写入数据的虚拟内存（例如： malloc 分配了100MB，但还没实际存入数据），同时也包含了所有加载的共享库
RSS	进程实际占用的物理内存总大小	包含了该进程独占的内存，以及所使用的所有共享库的完整大小
PSS	进程实际使用的私有物理内存 + 按比例平摊的共享库物理内存	包含了该进程独占的物理内存，以及按比例均摊的共享库物理内存（例如：3个进程共享了一个占 3MB 物理内存的动态库，那么每个进程的 PSS 统计中会增加 1MB）
USS	进程完全独占的物理内存	剔除了所有的共享库，只计算当前进程自己产生的、别人无法访问的物理内存
综上，PSS是准确度量进程的内存占用的指标，公平运行内存机制中统一采用 PSS 作为物理内存大小的统计指标。

2.1.3 应用物理内存统计
单进程的物理内存 PSS 大小可以通过如下方式获取：

import android.os.Debug;

// 方法一
Debug.MemoryInfo memoryInfo = new Debug.MemoryInfo();
Debug.getMemoryInfo(memoryInfo);

int totalPssKb = memoryInfo.getTotalPss(); //total PSS memory usage in kB.

// 方法二
//Retrieves the PSS memory used by the process as given by the smaps.
int totalPssKb = Debug.getPss(); 
API区别：Debug.getMemoryInfo(memoryInfo)除了可以获取总的内存大小，同时会对内存进行详细的分类和拆解，但是开销大；Debug.getPss()不会对内存进行分类，只会返回总的pss大小，开销较小。


性能影响：单次调用Debug.getMemoryInfo(memoryInfo).getTotalPss()可能会花费数十毫秒甚至更长时间，Debug.getPss()耗时更短，但高频率获取也会导致性能问题。


最佳实践：不要在 UI 线程、高频的定时器、或者类似 onDraw 这样的渲染回调中调用这个 API，可能会导致界面卡顿。建议在后台线程中，以较低的频率进行抓取统计。


应用物理内存： 是指应用中高优先级进程集合的PSS总和。

2.1.4 Java堆内存统计
与应用物理内存统计方式不同，每个 Java 进程的内存由独立的虚拟机进行管理。因此，应用中多个进程的Java堆内存单独统计。


Java堆内存大小可以通过如下方式进行获取：

// 采用Dalvik Heap Alloc的数值，代码如下：
Runtime runtime = Runtime.getRuntime();
long allocated = runtime.totalMemory() - runtime.freeMemory();
long maxMemory = runtime.maxMemory();

double heapUsage = (double) allocated / maxMemory;
2.2 软件机制介绍
当应用物理内存或者进程Java堆内存占用触达预警条件时，公平运行内存机制给应用进程发送内存预警广播，应用接收广播后应及时释放内存。若应用内存持续增长且触达查杀条件时，公平运行内存机制给应用进程发送查杀广播，应用接收到广播后应立即做现场数据备份，以保证再次使用时的接续体验。应用进程收到内存预警广播或查杀广播后，应通过回调接口将处理结果及时通知系统。公平运行内存机制接收到备份完成回调或者超时后查杀应用进程，并通过UI提示用户。

2.2.1 内存预警
应用收到内存预警广播后应当及时释放内存，以确保应用能够持久流畅运行。应用可以通过以下方式释放内存：

1）主动清理内存缓存：清理图片加载库的缓存或者清空业务 LruCache。

2）释放大型对象与解除引用：可以通过及时置空不再使用的大对象或者销毁页面中不需要的WebView来释放。

3）显式处理 Bitmap 和 Native 内存，不再需要用的对象显式执行回收方法及置空。

4）释放不可见页面的资源。

2.2.2 应用查杀
当应用接收到查杀广播通知时，表示应用的物理内存或者Java堆内存占用过高，已经或即将造成用户体验问题，需要通过查杀应用进程重置状态，以确保应用能持久流畅运行。应用接收到该广播后应立即保存现场数据，以便再次打开应用时能够恢复到之前的页面。应用应当在3秒内处理完上述操作并通过回调系统通知系统。

2.2.3 UI提示
当应用在前台且触达查杀条件后，公平运行内存机制给用户推送通知提示用户，物理内存和Java堆内存异常会有不同的UI提示方案：

1）Java堆内存异常：公平运行内存机制推送通知告知用户，用户可以选择关闭应用或者忽略消息。

2）物理内存异常：物理内存过量使用不仅会导致应用自身的体验问题，也会导致整机性能、功耗和稳定性问题，不及时关闭可能导致更严重的体验问题。公平运行内存机制会先查杀应用进程，再推送通知提示用户。

3. 适配指南
3.1 未适配的影响
应用完成公平运行内存机制适配后，可及时感知自身内存使用状态，并在内存异常被查杀前及时保存现场数据，从而降低风险并提升用户体验。未进行适配可能带来以下影响：

1）无法及时感知内存风险：应用无法获取内存使用状态及预警信息，可能错过最佳内存释放时机。当内存接近阈值时，系统会发送预警广播；未接入将无法响应，导致内存持续增长，影响运行稳定性。

2）无法有效保存现场数据：应用在被系统清理前无法接收备份广播，导致现场数据丢失，用户重新进入应用后无法延续使用，体验受损。

3）后台留存时长下降：缺乏预警与主动释放能力，应用在后台更易因高内存占用被清理，降低存活时长。

4）前台闪退风险增加：未能及时控制内存使用，可能在前台触发内存回收或被系统终止，提升闪退或异常退出的概率。

3.2 监听预警和异常广播
公平运行内存机制在合适时机对应用发送预警广播（TRIM）和异常查杀广播（KILL），具体如下：

字段	TRIM	KILL
action	itgsa.intent.action.TRIM	itgsa.intent.action.KILL
说明	通知应用释放内存	通知应用保存数据以便下次打开继续使用
额外数据存放

调用函数	key	value	数据类型
putExtra	BUNDLE_KEY_COMMON	common	Bundle
putExtra	BUNDLE_KEY_EXTRA	extra	Bundle
BUNDLE_KEY_COMMON公共字段

字段	key	value	说明	类型
异常类型	BUNDLE_KEY_NOTIFY_TYPE	notifyType	异常通知类型，包括物理内存异常和Java堆内存异常	int
异常ID	BUNDLE_KEY_NOTIFY_ID	notifyId	随机生成	int
异常原因	BUNDLE_KEY_REASON	reason	“Excessive PSS Usage”/“Excessive Java Heap Usage”	String
执行action	BUNDLE_KEY_ACTION	action	kill / trim	String
回调	BUNDLE_KEY_CALLBACK	callback	Binder对象	IBinder
异常通知类型

字段	类型id	数据类型
物理内存异常	1000	int
Java堆内存异常	2000	int
BUNDLE_KEY_EXTRA 额外字段，根据物理内存异常或Java堆内存异常放入不同的值，当notifyType 为1000时，extra 中包括物理内存大小及上限；当 notifyType 为 2000 时，extra 中包括堆内存大小及上限。

字段	key	value	数据类型	单位
java堆内存大小	BUNDLE_KEY_HEAP_ALLOC	heapAlloc	int	KB
java堆内存上限	BUNDLE_KEY_HEAP_CAPACITY	heapCapacity	int	KB
高优进程物理内存大小	BUNDLE_KEY_PSS	pss	int	KB
物理内存上限	BUNDLE_KEY_PSS_LIMIT	pssLimit	int	KB
3.3 及时返回处理结果
应用接收到广播后，应及时释放内存、备份数据，及时（需要在3秒内完成）通过回调接口返回处理结果。


应用端通过监听广播，获取 notifyType 、 notifyId 、IBinder 对象，通过Binder对象将内存释放/数据保存结果回调给系统端，系统端超时时间为 3s。通知回系统端的参数如下：

字段	含义	类型
notifyType	系统端广播发送的通知类型	int
notifyId	系统端广播发送的通知 ID	int
result	应用端处理结果	int
extra	额外数据	Bundle
result 字段不同值含义如下：

数值	含义
0	正确处理并释放或者保存
1	未正确处理
extra 中可以存放额外字段，暂时只存放一个字段，后续有变更的话可以在此进行扩展。

字段	类型	含义
reply	String	message
回调示例代码如下：

public void reply(int notifyType, int notifyId, int result, Bundle extra) {
    synchronized (this) {
        IBinder remote = mRemote;
        if (remote != null) {
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInt(notifyType);
                data.writeInt(notifyId);
                data.writeInt(result);
                if (extra == null) {
                    extra = new Bundle();
                }
                data.writeBundle(extra);
                remote.transact(TRANSACTION_EXCEPTION_REPLY, data, reply, IBinder.FLAG_ONEWAY);
                reply.readException();
            } catch (Exception e) {
                Log.e(TAG, "reply failed.", e);
            } finally {
                reply.recycle();
                data.recycle();
            }
        }
    }
}
3.4 完整代码示例
下面给出一个客户端广播接收及回调处理代码。

/**
 * 1) 实现接口IBinder.DeathRecipient
 * 2) 重写onReceived
 */
public class ExampleReceiver implements IBinder.DeathRecipient {

    private static final String TAG = "ExampleReceiver";
    private static final String ITGSA_ACTION = "itgsa.intent.action.TRIM"; //对应trim action
    public static final int TRANSACTION_EXCEPTION_REPLY = IBinder.FIRST_CALL_TRANSACTION;
    private IBinder mRemote;
    private boolean mInitialized;
    private Handler mHandler;

    @Override
    public void binderDied() {
        synchronized (this) {
            if (mRemote != null) {
                try {
                    mRemote.unlinkToDeath(this, 0);
                } catch (Exception ignore) {}
            }
            mRemote = null;
        }
    }

    private ExampleReceiver() {}

    public static ExampleReceiver getInstance() {
        return Instance.INSTANCE;
    }

    private static class Instance {
        private static final ExampleReceiver INSTANCE = new ExampleReceiver();
    }

    public void initialize(Context context) {
        synchronized (this) {
            if (!mInitialized) {
                HandlerThread ht = new HandlerThread(TAG);
                ht.start();
                mHandler = new Handler(ht.getLooper());
                IntentFilter filter = new IntentFilter(ITGSA_ACTION);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.registerReceiver(mReceiver, filter, null, mHandler, Context.RECEIVER_EXPORTED);
                } else {
                    context.registerReceiver(mReceiver, filter, null, mHandler);
                }
                // Process other initialize step
                // ...
                mInitialized = true;
            }
        }
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ITGSA_ACTION.equals(intent.getAction())) {
                Bundle data = intent.getExtras();
                if (data == null) return;
                // Get common data from the intent
                Bundle bundle = data.getBundle("common");
                if (bundle == null) return;
                int notifyType = bundle.getInt("notifyType");
                int notifyId = bundle.getInt("notifyId");
                String reason = bundle.getString("reason");
                String action = bundle.getString("action");
                IBinder callbackBinder = bundle.getBinder("callback");
                // Get extra data from the intent
                Bundle extraData = data.getBundle("extra");
                if (extraData == null) return;
                // Get java or physical memory usage according to notifyType notifyId
                int heapAlloc = extraData.getInt("heapAlloc");
                int heapCapacity = extraData.getInt("heapCapacity");
                int pss = extraData.getInt("pss");
                int pssLimit = extraData.getInt("pssLimit");
                if (callbackBinder != null) {
                    // Process the CALLBACK_BINDER as needed
                    // ...
                    handleReceived(notifyType, notifyId, callbackBinder, bundle);
                } else {
                    Log.w(TAG, "CALLBACK_BINDER not found in intent extras.");
                }
            }
        }
    };

    private void handleReceived(int notifyType, int notifyId, IBinder callback, Bundle extra) {
        if (checkRemote(callback)) {
            // Save Activity Record
            Bundle data = new Bundle();
            data.putString("reply", "example reply data transfer");
            reply(notifyType, notifyId, 0, data);
        }
    }

    private boolean checkRemote(IBinder callback) {
        synchronized (this) {
            if (mRemote == null) {
                try {
                    mRemote = callback;
                    mRemote.linkToDeath(this, 0);
                } catch (RemoteException e) {
                    mRemote = null;
                    return false;
                }
            }
        }
        return true;
    }

    public void reply(int notifyType, int notifyId, int result, Bundle extra) {
        synchronized (this) {
            IBinder remote = mRemote;
            if (remote != null) {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInt(notifyType);
                    data.writeInt(notifyId);
                    data.writeInt(result);
                    if (extra == null) {
                        extra = new Bundle();
                    }
                    data.writeBundle(extra);
                    remote.transact(TRANSACTION_EXCEPTION_REPLY, data, reply, IBinder.FLAG_ONEWAY);
                    reply.readException();
                } catch (Exception e) {
                    Log.e(TAG, "reply failed.", e);
                } finally {
                    reply.recycle();
                    data.recycle();
                }
            }
        }
    }
}