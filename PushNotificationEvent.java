package com.dsoft.m2u.event;


import org.springframework.context.ApplicationEvent;

import com.dsoft.m2u.api.request.PushNotificationRequest;
import com.dsoft.m2u.domain.Order;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PushNotificationEvent extends ApplicationEvent{

	private static final long serialVersionUID = -586680304301329936L;
	
	private PushNotificationRequest request;
	private String fireBaseId;
	private Order order;
	
	public PushNotificationEvent(Object source) {
		super(source);
	}
	
	public PushNotificationEvent(Object source, PushNotificationRequest request) {
		super(source);
		this.request = request;
	}

	public PushNotificationEvent(Object source, String fireBaseId, Order order) {
		super(source);
		this.fireBaseId = fireBaseId;
		this.order = order;
	}
	
}
