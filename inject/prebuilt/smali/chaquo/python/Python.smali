.class public Lcom/chaquo/python/Python;
.super Ljava/lang/Object;
.source "Python.java"


# annotations
.annotation system Ldalvik/annotation/MemberClasses;
    value = {
        Lcom/chaquo/python/Python$Platform;
    }
.end annotation


# static fields
.field private static failed:Z

.field private static instance:Lcom/chaquo/python/Python;

.field private static platform:Lcom/chaquo/python/Python$Platform;


# direct methods
.method static constructor <clinit>()V
    .registers 1

    .line 26
    new-instance v0, Lcom/chaquo/python/Python;

    invoke-direct {v0}, Lcom/chaquo/python/Python;-><init>()V

    sput-object v0, Lcom/chaquo/python/Python;->instance:Lcom/chaquo/python/Python;

    return-void
.end method

.method private constructor <init>()V
    .registers 1

    .line 79
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method

.method public static declared-synchronized getInstance()Lcom/chaquo/python/Python;
    .registers 2

    const-class v0, Lcom/chaquo/python/Python;

    monitor-enter v0

    .line 32
    :try_start_3
    invoke-static {}, Lcom/chaquo/python/Python;->isStarted()Z

    move-result v1

    if-nez v1, :cond_11

    .line 33
    new-instance v1, Lcom/chaquo/python/GenericPlatform;

    invoke-direct {v1}, Lcom/chaquo/python/GenericPlatform;-><init>()V

    invoke-static {v1}, Lcom/chaquo/python/Python;->start(Lcom/chaquo/python/Python$Platform;)V

    .line 35
    :cond_11
    sget-object v1, Lcom/chaquo/python/Python;->instance:Lcom/chaquo/python/Python;
    :try_end_13
    .catchall {:try_start_3 .. :try_end_13} :catchall_15

    monitor-exit v0

    return-object v1

    .line 31
    :catchall_15
    move-exception v1

    :try_start_16
    monitor-exit v0
    :try_end_17
    .catchall {:try_start_16 .. :try_end_17} :catchall_15

    throw v1
.end method

.method private native getModuleNative(Ljava/lang/String;)J
.end method

.method public static declared-synchronized getPlatform()Lcom/chaquo/python/Python$Platform;
    .registers 2

    const-class v0, Lcom/chaquo/python/Python;

    monitor-enter v0

    .line 65
    :try_start_3
    sget-object v1, Lcom/chaquo/python/Python;->platform:Lcom/chaquo/python/Python$Platform;
    :try_end_5
    .catchall {:try_start_3 .. :try_end_5} :catchall_7

    monitor-exit v0

    return-object v1

    .line 65
    :catchall_7
    move-exception v1

    :try_start_8
    monitor-exit v0
    :try_end_9
    .catchall {:try_start_8 .. :try_end_9} :catchall_7

    throw v1
.end method

.method public static declared-synchronized isStarted()Z
    .registers 2

    const-class v0, Lcom/chaquo/python/Python;

    monitor-enter v0

    .line 70
    :try_start_3
    sget-object v1, Lcom/chaquo/python/Python;->platform:Lcom/chaquo/python/Python$Platform;
    :try_end_5
    .catchall {:try_start_3 .. :try_end_5} :catchall_c

    if-eqz v1, :cond_9

    const/4 v1, 0x1

    goto :goto_a

    :cond_9
    const/4 v1, 0x0

    :goto_a
    monitor-exit v0

    return v1

    .line 70
    :catchall_c
    move-exception v1

    :try_start_d
    monitor-exit v0
    :try_end_e
    .catchall {:try_start_d .. :try_end_e} :catchall_c

    throw v1
.end method

.method public static declared-synchronized start(Lcom/chaquo/python/Python$Platform;)V
    .registers 4
    .param p0, "platform"    # Lcom/chaquo/python/Python$Platform;

    const-class v0, Lcom/chaquo/python/Python;

    monitor-enter v0

    .line 45
    :try_start_3
    invoke-static {}, Lcom/chaquo/python/Python;->isStarted()Z

    move-result v1

    if-nez v1, :cond_2b

    .line 48
    sget-boolean v1, Lcom/chaquo/python/Python;->failed:Z
    :try_end_b
    .catchall {:try_start_3 .. :try_end_b} :catchall_33

    if-nez v1, :cond_23

    .line 53
    :try_start_d
    invoke-virtual {p0}, Lcom/chaquo/python/Python$Platform;->getPath()Ljava/lang/String;

    move-result-object v1

    invoke-static {p0, v1}, Lcom/chaquo/python/Python;->startNative(Lcom/chaquo/python/Python$Platform;Ljava/lang/String;)V

    .line 54
    sget-object v1, Lcom/chaquo/python/Python;->instance:Lcom/chaquo/python/Python;

    invoke-virtual {p0, v1}, Lcom/chaquo/python/Python$Platform;->onStart(Lcom/chaquo/python/Python;)V

    .line 55
    sput-object p0, Lcom/chaquo/python/Python;->platform:Lcom/chaquo/python/Python$Platform;
    :try_end_1b
    .catchall {:try_start_d .. :try_end_1b} :catchall_1e

    .line 59
    nop

    .line 60
    monitor-exit v0

    return-void

    .line 56
    :catchall_1e
    move-exception v1

    .line 57
    .local v1, "e":Ljava/lang/Throwable;
    const/4 v2, 0x1

    :try_start_20
    sput-boolean v2, Lcom/chaquo/python/Python;->failed:Z

    .line 58
    throw v1

    .line 50
    .end local v1    # "e":Ljava/lang/Throwable;
    :cond_23
    new-instance v1, Ljava/lang/IllegalStateException;

    const-string v2, "Python startup previously failed, and cannot be retried"

    invoke-direct {v1, v2}, Ljava/lang/IllegalStateException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 46
    :cond_2b
    new-instance v1, Ljava/lang/IllegalStateException;

    const-string v2, "Python already started"

    invoke-direct {v1, v2}, Ljava/lang/IllegalStateException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 44
    .end local p0    # "platform":Lcom/chaquo/python/Python$Platform;
    :catchall_33
    move-exception p0

    monitor-exit v0
    :try_end_35
    .catchall {:try_start_20 .. :try_end_35} :catchall_33

    throw p0
.end method

.method private static native startNative(Lcom/chaquo/python/Python$Platform;Ljava/lang/String;)V
.end method


# virtual methods
.method public getBuiltins()Lcom/chaquo/python/PyObject;
    .registers 2

    .line 93
    const-string v0, "builtins"

    invoke-virtual {p0, v0}, Lcom/chaquo/python/Python;->getModule(Ljava/lang/String;)Lcom/chaquo/python/PyObject;

    move-result-object v0

    return-object v0
.end method

.method public getModule(Ljava/lang/String;)Lcom/chaquo/python/PyObject;
    .registers 4
    .param p1, "name"    # Ljava/lang/String;

    .line 84
    invoke-direct {p0, p1}, Lcom/chaquo/python/Python;->getModuleNative(Ljava/lang/String;)J

    move-result-wide v0

    invoke-static {v0, v1}, Lcom/chaquo/python/PyObject;->getInstance(J)Lcom/chaquo/python/PyObject;

    move-result-object v0

    invoke-static {v0}, Ljava/util/Objects;->requireNonNull(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object v0

    check-cast v0, Lcom/chaquo/python/PyObject;

    return-object v0
.end method
