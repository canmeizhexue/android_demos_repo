.class public Lcom/canmeizhexue/andfixdemo/MainActivity_CF;
.super Landroid/app/Activity;
.source "MainActivity.java"


# direct methods
.method public constructor <init>()V
    .locals 0

    .prologue
    .line 8
    invoke-direct {p0}, Landroid/app/Activity;-><init>()V

    return-void
.end method

.method private getString()Ljava/lang/String;
    .locals 1

    .prologue
    .line 26
    invoke-static {}, Lcom/canmeizhexue/andfixdemo/OldClass;->getString()Ljava/lang/String;

    move-result-object v0

    return-object v0
.end method


# virtual methods
.method protected onCreate(Landroid/os/Bundle;)V
    .locals 2
    .param p1, "savedInstanceState"    # Landroid/os/Bundle;

    .prologue
    .line 12
    invoke-super {p0, p1}, Landroid/app/Activity;->onCreate(Landroid/os/Bundle;)V

    .line 13
    const v1, 0x7f04001a

    invoke-virtual {p0, v1}, Lcom/canmeizhexue/andfixdemo/MainActivity_CF;->setContentView(I)V

    .line 14
    const v1, 0x7f0b0054

    invoke-virtual {p0, v1}, Lcom/canmeizhexue/andfixdemo/MainActivity_CF;->findViewById(I)Landroid/view/View;

    move-result-object v0

    check-cast v0, Landroid/widget/TextView;

    .line 15
    .local v0, "textView":Landroid/widget/TextView;
    invoke-direct {p0}, Lcom/canmeizhexue/andfixdemo/MainActivity_CF;->getString()Ljava/lang/String;

    move-result-object v1

    invoke-virtual {v0, v1}, Landroid/widget/TextView;->setText(Ljava/lang/CharSequence;)V

    .line 17
    return-void
.end method

.method protected onResume()V
    .locals 2

    .prologue
    .line 21
    invoke-super {p0}, Landroid/app/Activity;->onResume()V

    .line 22
    const-string v0, "silence"

    const-string v1, "----\u6211\u8fd8\u52a0\u65e5\u5fd7\u4e86"

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 23
    return-void
.end method
