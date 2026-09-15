package com.pkj.leanbulk.notify

import android.service.notification.NotificationListenerService

/**
 * **비어 있는 게 맞다.** 안드로이드는 다른 앱의 재생 정보(곡 제목·진행 위치)를 "알림 접근"
 * 권한을 가진 앱에게만 준다. 그 권한은 이런 서비스를 선언한 앱만 받을 수 있어서 자리만
 * 만들어 둔다. 알림은 하나도 읽지 않는다.
 */
class MediaAccessService : NotificationListenerService()
