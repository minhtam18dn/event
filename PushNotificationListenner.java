package com.dsoft.m2u.event.listenner;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import com.dsoft.m2u.api.request.PushNotificationRequest;
import com.dsoft.m2u.domain.Device;
import com.dsoft.m2u.domain.FirebaseLog;
import com.dsoft.m2u.domain.Notification;
import com.dsoft.m2u.domain.NotificationType;
import com.dsoft.m2u.domain.OrderStatus;
import com.dsoft.m2u.event.PushNotificationEvent;
import com.dsoft.m2u.push.notification.service.FCMService;
import com.dsoft.m2u.repository.DeviceRepository;
import com.dsoft.m2u.repository.FirebaseLogRepository;
import com.dsoft.m2u.repository.NotificationRepository;

@Component
public class PushNotificationListenner implements ApplicationListener<PushNotificationEvent> {

	private static final Logger logger = LogManager.getLogger(PushNotificationListenner.class);
	@Autowired
	private FCMService fcmService;

	@Autowired
	private NotificationRepository notificationRepository;

	@Autowired
	private DeviceRepository deviceRepository;
	
	@Autowired
	private FirebaseLogRepository firebaseLogRepository;

	@Override
	public void onApplicationEvent(PushNotificationEvent event) {
		logger.info("PushNotificationListenner ");
		
		String title = "Đơn hàng "+event.getOrder().getOrderNo();
		String mess = "";
		
		if(event.getOrder().getStatus().equals(OrderStatus.NEW)) {
			mess = "Đã tạo mới";
		}else if(event.getOrder().getStatus().equals(OrderStatus.PAID)) {
			mess = "Đã thanh toán thành công. Vui lòng chờ kiểm duyệt.";
		}else if(event.getOrder().getStatus().equals(OrderStatus.APPROVED)) {
			mess = "Đã được chấp nhận.";
		}else if(event.getOrder().getStatus().equals(OrderStatus.REJECTED)) {
			mess = "Đã bị từ chối.";
		}

		PushNotificationRequest request = new PushNotificationRequest();
		
		request.setTitle(title);
		request.setTopic("Me2u");
		request.setMessage(mess);
		
		Map<String, Object> dataPayload = new HashMap<String, Object>();
		dataPayload.put("type", "ORDER");
		dataPayload.put("id", event.getOrder().getId());
		dataPayload.put("status", event.getOrder().getStatus().toString());
		dataPayload.put("orderNo", event.getOrder().getOrderNo());
		dataPayload.put("content", "Content not yet define :)");
		request.setCustomerDataPayload(dataPayload);
		
		Map<String, String> data = new HashMap<String, String>();
		data.put("type", "ORDER");
		data.put("id", event.getOrder().getId());
		data.put("status", event.getOrder().getStatus().toString());
		data.put("orderNo", event.getOrder().getOrderNo());
		data.put("content", "Content not yet define :)");
		request.setData(data);
		
		List<Device> devices = this.deviceRepository.findByUserFireBaseId(event.getFireBaseId());
		if (devices != null) {
			for (Device device : devices) {
				request.setToken(device.getId());
				try {
					fcmService.sendMessage(request);
				} catch (InterruptedException e) {
					e.printStackTrace();
					firebaseLogRepository.save(new FirebaseLog(device.getId(), mess, e.getMessage()));
				} catch (ExecutionException e) {
					e.printStackTrace();
					firebaseLogRepository.save(new FirebaseLog(device.getId(), mess, e.getMessage()));
				}
			}
			Notification notification = new Notification(event.getOrder().getCreatedBy(), NotificationType.ORDER, event.getOrder().getId(), event.getOrder().getStatus(), mess, true);
			this.notificationRepository.save(notification);
		}		
	}
}
