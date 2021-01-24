package com.dsoft.m2u.service;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dsoft.m2u.api.dto.ErrorCodeEnum;
import com.dsoft.m2u.api.dto.MobileFacilityDTO;
import com.dsoft.m2u.api.dto.MobileOrderResDTO;
import com.dsoft.m2u.api.dto.MobileTimeRejectResDTO;
import com.dsoft.m2u.api.dto.MobileTimeResDTO;
import com.dsoft.m2u.api.dto.OrderDetailDTO;
import com.dsoft.m2u.api.dto.OrderResDTO;
import com.dsoft.m2u.api.dto.OrderStoryDTO;
import com.dsoft.m2u.api.dto.OrderStoryRequestDTO;
import com.dsoft.m2u.api.dto.OrderTimeDTO;
import com.dsoft.m2u.api.dto.OrderUpdateDTO;
import com.dsoft.m2u.api.dto.PaymentResDTO;
import com.dsoft.m2u.api.dto.TimeResDTO;
import com.dsoft.m2u.api.request.MobileOrderRequest;
import com.dsoft.m2u.api.request.OrderRequest;
import com.dsoft.m2u.api.request.OrderReviewerUpdateRequest;
import com.dsoft.m2u.api.response.BaseResponse;
import com.dsoft.m2u.common.CommonConstants;
import com.dsoft.m2u.common.CommonFunctions;
import com.dsoft.m2u.common.ScreenMessageConstants;
import com.dsoft.m2u.domain.CategoryTranslate;
import com.dsoft.m2u.domain.ConditionPrice;
import com.dsoft.m2u.domain.Facility;
import com.dsoft.m2u.domain.FacilityTranslate;
import com.dsoft.m2u.domain.LanguageCode;
import com.dsoft.m2u.domain.Order;
import com.dsoft.m2u.domain.OrderStatus;
import com.dsoft.m2u.domain.OrderType;
import com.dsoft.m2u.domain.Payment;
import com.dsoft.m2u.domain.PriceBySlot;
import com.dsoft.m2u.domain.Slot;
import com.dsoft.m2u.domain.SlotType;
import com.dsoft.m2u.domain.Story;
import com.dsoft.m2u.domain.Template;
import com.dsoft.m2u.domain.TimeConfig;
import com.dsoft.m2u.domain.TimeConfigType;
import com.dsoft.m2u.domain.User;
import com.dsoft.m2u.domain.specification.OrderSpecs;
import com.dsoft.m2u.event.PushNotificationEvent;
import com.dsoft.m2u.exception.ResourceInvalidInputException;
import com.dsoft.m2u.exception.ResourceNotFoundException;
import com.dsoft.m2u.repository.FacilityRepository;
import com.dsoft.m2u.repository.OrderRepository;
import com.dsoft.m2u.repository.PaymentRepository;
import com.dsoft.m2u.repository.PriceBySlotRepository;
import com.dsoft.m2u.repository.SlotRepository;
import com.dsoft.m2u.repository.SlotTypeRepository;
import com.dsoft.m2u.repository.StoryRepository;
import com.dsoft.m2u.repository.TemplateRepository;
import com.dsoft.m2u.repository.TimeConfigRepository;
import com.dsoft.m2u.repository.UserRepository;
import com.dsoft.m2u.utils.MapperUtil;
/**
 * [Description]:<br>
 * [ Remarks ]:<br>
 * [Copyright]: Copyright (c) 2020<br>
 * 
 * @author D-Soft Joint Stock Company
 * @version 1.0
 */
@Service
public class OrderService {

	private static final Logger logger = LogManager.getLogger(OrderService.class);

	@Autowired
	private OrderRepository orderRepository;

	@Autowired
	private TemplateRepository templateRepository;

	@Autowired
	private FacilityRepository facilityRepository;

	@Autowired
	private TimeConfigRepository timeConfigRepository;

	@Autowired
	private SlotRepository slotRepository;

	@Autowired
	private PaymentRepository paymentRepository;
	
	@Autowired
	private PriceBySlotRepository priceBySlotRepository;
	
	@Autowired
	private StoryRepository storyRepository;
	
	@Autowired
	private SlotTypeRepository slotTypeRepository;
	
	@Autowired
	private UserRepository userRepository;
	
	@Autowired
	private ApplicationEventPublisher publisher;

	public BaseResponse getAll(Integer pageNo, Integer pageSize, String searchText, List<String> facilityIds, List<String> templateIds, List<String> categoryIds, 
			String date, String deviceId, List<String> listStatus, List<String> listType, String dateFrom, String dateTo) {
		logger.info("OrderService.getAll");
		Pageable pageable = PageRequest.of(pageNo, pageSize, Sort.by("createdAt").descending());
		Specification<Order> spec = Specification.where(OrderSpecs.getOrderByActiveSpec(true));
		if (searchText != null && !searchText.isEmpty()) {
			spec = spec.and(OrderSpecs.getOrderByFacilityTitleSpec(searchText))
					.or(OrderSpecs.getOrderByMessageSpec(searchText));
		}
		if(templateIds != null) {
			spec = spec.and(OrderSpecs.getOrderByTemplateIdsSpec(templateIds));
		}
		if(categoryIds != null) {
			spec = spec.and(OrderSpecs.getOrderByCategoryIdsSpec(categoryIds));
		}
		if(facilityIds != null) {
			spec = spec.and(OrderSpecs.getOrderByFacilityIdsSpec(facilityIds));
		}		
		if (listStatus != null && listStatus.size() != 0) {
			List<OrderStatus> status = OrderStatus.fromValue(listStatus);  
			spec = spec.and(OrderSpecs.getTemplateByStatusesSpec(status));
		}
		if (listType != null && listType.size() != 0) {
			List<OrderType> types = OrderType.fromValue(listType);  
			spec = spec.and(OrderSpecs.getTemplateByTypesSpec(types));
		}
		if (dateFrom != null && !dateFrom.isEmpty() || dateTo != null && !dateTo.isEmpty()) {
			if (dateFrom == null) {
				spec = spec.and(OrderSpecs.getOrderLessThanCreatedAt(CommonFunctions.convertStringToDateObject(dateTo)));
			}
			if (dateTo == null) {
				spec = spec.and(OrderSpecs.getOrderGreaterThanCreatedAt(CommonFunctions.convertStringToDateObject(dateFrom)));
			}
			if (dateFrom != null && dateTo != null) {
				spec = spec.and(OrderSpecs.getOrderBetweenCreatedAt(CommonFunctions.convertStringToDateObject(dateFrom),
						CommonFunctions.convertStringToDateObject(dateTo)));
			}
		}
		Page<Order> pagedResult = orderRepository.findAll(spec, pageable);
		List<OrderResDTO> orderResponses = new ArrayList<>();
		Integer totalItem = (int) orderRepository.count(spec);
		if (pagedResult.hasContent()) {
			for (Order order : pagedResult.getContent()) {
				OrderResDTO orderResDTO = MapperUtil.mapper(order, OrderResDTO.class);
				for (FacilityTranslate facilityTranslate : order.getFacility().getTranslates()) {
					if (facilityTranslate.getCode() == LanguageCode.VI) {
						orderResDTO.setFacilityTitleVi(facilityTranslate.getTitle());
					}
					if (facilityTranslate.getCode() == LanguageCode.EN) {
						orderResDTO.setFacilityTitleEn(facilityTranslate.getTitle());
					}
				}				
				orderResDTO.setAmount(CommonFunctions.formatDoubleToString(order.getAmount()));
				orderResDTO.setUnitPrice("0");
				//get list story
				List<OrderStoryDTO> storyDTOs = new ArrayList<>();	
				storyRepository.findByOrderId(order.getId()).forEach(story -> {
					OrderStoryDTO storyDTO = new OrderStoryDTO();
					storyDTO.setMessage(story.getMessage());
					storyDTO.setPriority(story.getPriority());
					if(story.getTemplate() != null) {
						storyDTO.setTemplateName(story.getTemplate().getNameDsp());
						storyDTO.setImageURI(story.getTemplate().getImageUri());
						storyDTO.setDurationTemplate(story.getTemplate().getDuration());
						for (CategoryTranslate categoryTranslate : story.getTemplate().getCategory().getTranslates()) {
							if (categoryTranslate.getCode() == LanguageCode.VI) {
								storyDTO.setTemplateCategoryNameVi(categoryTranslate.getName());
							}
							if (categoryTranslate.getCode() == LanguageCode.EN) {
								storyDTO.setTemplateCategoryNameEn(categoryTranslate.getName());
							}
						}
					}
					storyDTOs.add(storyDTO);
				});
				
				orderResDTO.setStories(storyDTOs);
				orderResponses.add(orderResDTO);
			}	
		}
		return new BaseResponse(ScreenMessageConstants.SUCCESS, orderResponses, totalItem);
	}
	
	public BaseResponse getAllOrderByFireBaseIdForMoble(String fireBaseId, List<String> statuses, Integer pageNo, Integer pageSize) {
		logger.info("OrderService.getAllOrderByFireBaseIdForMoble");
		try {
			List<Order> listOrders = new ArrayList<Order>();
			List<MobileOrderResDTO> mobileOrderResDTOs = new ArrayList<>();

			Pageable pageable = PageRequest.of(pageNo, pageSize, Sort.by("createdAt").descending());
			Specification<Order> spec = Specification.where(OrderSpecs.getOrderByActiveSpec(true));
			
			spec = spec.and(OrderSpecs.getOrderByFireBaseID(fireBaseId));
			
			if (statuses != null && statuses.size() != 0) {
				List<OrderStatus> status = OrderStatus.fromValue(statuses);  
				spec = spec.and(OrderSpecs.getTemplateByStatusesSpec(status));
			}
			
			Page<Order> pagedResult = orderRepository.findAll(spec, pageable);
			Integer totalItem = (int) orderRepository.count(spec);
			
			listOrders = pagedResult.getContent();

			for (Order order : listOrders) {
				if (order.getType().equals(OrderType.B2C)) {
					MobileOrderResDTO responseDTO = new MobileOrderResDTO();
					responseDTO.setId(order.getId());
					responseDTO.setAmount(CommonFunctions.formatDoubleToString(order.getAmount()));
					responseDTO.setStatus(order.getStatus().toString());
					responseDTO.setOrderNo(order.getOrderNo());
					List<OrderTimeDTO> listOrderTimes = new ArrayList<>();
					for (Slot slot : slotRepository.findByOrderIdAndActive(order.getId(), true)) {
						listOrderTimes.add(new OrderTimeDTO(slot.getTimeStart().toString(), slot.getTimeEnd().toString(), slot.getDateDisplay().toString()));
					}
					responseDTO.setTimes(listOrderTimes);
					
					List<OrderStoryDTO> storyDTOs = new ArrayList<>();	
					storyRepository.findByOrderIdOrderByPriority(order.getId()).forEach(story -> {
						OrderStoryDTO storyDTO = new OrderStoryDTO();
						storyDTO.setMessage(story.getMessage());
						storyDTO.setPriority(story.getPriority());
						if(story.getTemplate() != null) {
							storyDTO.setTemplateName(story.getTemplate().getNameDsp());
							storyDTO.setImageURI(story.getTemplate().getImageUri());
							storyDTO.setDurationTemplate(story.getTemplate().getDuration());
							for (CategoryTranslate categoryTranslate : story.getTemplate().getCategory().getTranslates()) {
								if (categoryTranslate.getCode() == LanguageCode.VI) {
									storyDTO.setTemplateCategoryNameVi(categoryTranslate.getName());
								}
								if (categoryTranslate.getCode() == LanguageCode.EN) {
									storyDTO.setTemplateCategoryNameEn(categoryTranslate.getName());
								}
							}
						}
						storyDTOs.add(storyDTO);
					});
					
					responseDTO.setStories(storyDTOs);
					
					MobileFacilityDTO mobileFacilityDTO = new MobileFacilityDTO();
					mobileFacilityDTO.setId(order.getFacility().getId());
					mobileFacilityDTO.setLocation(order.getFacility().getLocation());					
					for (FacilityTranslate facilityTranslate : order.getFacility().getTranslates()) {
						if (facilityTranslate.getCode() == LanguageCode.VI) {
							mobileFacilityDTO.setTitleVi(facilityTranslate.getTitle());
						}
						if (facilityTranslate.getCode() == LanguageCode.EN) {
							mobileFacilityDTO.setTitleEn(facilityTranslate.getTitle());
						}
					}
										
					responseDTO.setFacility(mobileFacilityDTO);
					
					mobileOrderResDTOs.add(responseDTO);
				}
			}

			return new BaseResponse(ScreenMessageConstants.SUCCESS, mobileOrderResDTOs, totalItem);
		} catch (Exception e) {
			logger.info(ScreenMessageConstants.FAILED, e);
			throw new ResourceInvalidInputException(ScreenMessageConstants.FAILED);
		}
	}

	@Transactional
	public BaseResponse insert(OrderRequest request) {
		logger.info("OrderService.insert");
		
		//not now
		return new BaseResponse(ScreenMessageConstants.SUCCESS, "Save order successfully!");
	}

	public BaseResponse getById(String id) {
		logger.info("OrderService.getById");
		Order order = orderRepository.findByIdAndActive(id, true).orElseThrow(
				() -> new ResourceNotFoundException("Order", "id", id));
				
		OrderDetailDTO orderDatailRes = MapperUtil.mapper(order, OrderDetailDTO.class);
		orderDatailRes.setOrderDateCreate(CommonFunctions.convertDateToLocalDate(order.getCreatedAt()).toString());
		orderDatailRes.setAmount(CommonFunctions.formatDoubleToString(order.getAmount()));
		orderDatailRes.setUnitPrice("0");
		//get list story
		List<OrderStoryDTO> storyDTOs = new ArrayList<>();	
		storyRepository.findByOrderId(order.getId()).forEach(story -> {
			OrderStoryDTO storyDTO = new OrderStoryDTO();
			storyDTO.setMessage(story.getMessage());
			storyDTO.setPriority(story.getPriority());
			if(story.getTemplate() != null) {			
				storyDTO.setTemplateName(story.getTemplate().getNameDsp());
				storyDTO.setImageURI(story.getTemplate().getImageUri());
				storyDTO.setDurationTemplate(story.getTemplate().getDuration());
				for (CategoryTranslate categoryTranslate : story.getTemplate().getCategory().getTranslates()) {
					if (categoryTranslate.getCode() == LanguageCode.VI) {
						storyDTO.setTemplateCategoryNameVi(categoryTranslate.getName());
					}
					if (categoryTranslate.getCode() == LanguageCode.EN) {
						storyDTO.setTemplateCategoryNameEn(categoryTranslate.getName());
					}
				}
			}
			storyDTOs.add(storyDTO);
		});
		
		orderDatailRes.setStories(storyDTOs);
		//get list payment
		List<Payment> listPayments = paymentRepository.findByActiveAndOrderId(true, order.getId());
		if(listPayments.size() == 1) {
			Payment payment = listPayments.get(0);
			PaymentResDTO paymentRes = MapperUtil.mapper(payment, PaymentResDTO.class);
			paymentRes.setPaymentDate(CommonFunctions.convertDateToLocalDate(payment.getCreatedAt()).toString());
			paymentRes.setAmount(CommonFunctions.formatDoubleToString(payment.getAmount()));
			orderDatailRes.setPayments(paymentRes);
		}
					
		return new BaseResponse(ScreenMessageConstants.SUCCESS, orderDatailRes);
	}

	public BaseResponse update(OrderUpdateDTO dto, String id) {
		logger.info("OrderService.update");

		Order order = orderRepository.findByIdAndActive(id, true)
				.orElseThrow(() -> new ResourceNotFoundException(ErrorCodeEnum.ORDER_ID_INVALID, "Order", "id", id));
		order.setStatus(OrderStatus.fromValue(dto.getStatus()));
		if (order.getStatus().equals(OrderStatus.APPROVED)) {
			if(order.getType().equals(OrderType.B2C)) {
				//push notification
				PushNotificationEvent event = new PushNotificationEvent(this, order.getCreatedBy(), order);
				publisher.publishEvent(event);
			}
		}

		order.setUpdatedAt(new Date());
		orderRepository.save(order);
		return new BaseResponse(ScreenMessageConstants.SUCCESS, "Update order successfully!");
	}

	public BaseResponse delete(String id) {
		logger.info("OrderService.delete");
		Order order = orderRepository.findByIdAndActive(id, true)
				.orElseThrow(() -> new ResourceNotFoundException(ErrorCodeEnum.ORDER_ID_INVALID, "Order", "id", id));
		order.setActive(false);
		order.setUpdatedAt(new Date());

		List<Slot> slots = slotRepository.findByOrderIdAndActive(order.getId(), true);

		for (Slot slot : slots) {
			slot.setActive(false);
			slot.setUpdatedAt(new Date());
			slotRepository.save(slot);
		}
		orderRepository.save(order);
		return new BaseResponse(ScreenMessageConstants.SUCCESS, "Delete order successfully");
	}
	
	@Transactional
	public BaseResponse createOrderForMobile(MobileOrderRequest request) {
		logger.info("OrderService.createOrderForMobile");
		if(request.getTime() == null || request.getTime().size() > 5 ) {
			new ResourceInvalidInputException(ErrorCodeEnum.ORDER_LIMIT, "Order has only maximum 5 slot.");
		}
		if(request.getTime().size() > 1) {
			request.setTime(checkAndRemoveDuplicateSlot(request.getTime()));
		}
					
		Facility facility = facilityRepository.findByIdAndActive(request.getFacilityId(), true)
				.orElseThrow(() -> new ResourceNotFoundException(ErrorCodeEnum.FACILITY_NOT_FOUND, "Facility", "id", request.getFacilityId()));
		
		Optional<User> userOpt = this.userRepository.findByFireBaseId(request.getFireBaseId());
		if(!userOpt.isPresent()) {
			throw new ResourceNotFoundException(ErrorCodeEnum.USER_FIREBASE_NOT_FOUND, "User", "id", request.getFireBaseId());
		}
		
		List<Slot> slotCompare = new ArrayList<Slot>();
		for(OrderTimeDTO orderTimeDto : request.getTime()) {
			findQualityAndCheckOrderTime(orderTimeDto.getDateDisplay(), orderTimeDto.getStartTime(), orderTimeDto.getEndTime(), facility.getId());
			slotCompare.addAll(slotRepository.findByOrderFacilityIdAndActiveAndDateDisplayAndTimeStart(
					facility.getId(), true, CommonFunctions.convertStringToLocalDateObject(orderTimeDto.getDateDisplay()), LocalTime.parse(orderTimeDto.getStartTime())));
		}
		
		if(slotCompare.size() != 0) {
			for(Slot slot : slotCompare) {
				Order order = orderRepository.findByIdAndActive(slot.getOrder().getId(), true).orElse(null);
				if(order != null) {
					long diffInMillies = CommonFunctions.getDateDiff(order.getCreatedAt(), new Date(), TimeUnit.MINUTES);
					if(order.getStatus() == OrderStatus.PAID || order.getStatus() == OrderStatus.APPROVED || (order.getStatus() == OrderStatus.NEW 
							&& diffInMillies < CommonConstants.SLOT_TIME_CONDITION)) {
						throw new ResourceInvalidInputException(ErrorCodeEnum.ORDER_TIME_IS_USED, "This time have already used: " +  slot.getTimeStart() +
								" - " + slot.getTimeEnd());
					}
				}
			}
		}
		//Create order		
		Order order = new Order(request.getTime().size(), OrderType.B2C);
		
		Set<Slot> slots = new HashSet<>();
		double totolPrice = 0d;
		
		//Create slot		
		for(OrderTimeDTO orderTimeDto : request.getTime()) {
			LocalDate date = CommonFunctions.convertStringToLocalDateObject(orderTimeDto.getDateDisplay());
			double price = CommonConstants.PRICE_DEFAULT;
			PriceBySlot priceBySlot = null;
			String conditionPrice = ConditionPrice.NORMAL.name();
			if(date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
				conditionPrice = ConditionPrice.WEEKEND.name();
			}
			else {
				conditionPrice = ConditionPrice.NORMAL.name();
				List<SlotType> slotTypes = slotTypeRepository.getSlotTypeBySpecialDate(date.getDayOfMonth(), date.getMonthValue());
				if(slotTypes != null && !slotTypes.isEmpty()) {
					conditionPrice = slotTypes.get(0).getId();
				}
			}			
			priceBySlot = priceBySlotRepository.findByStartTimeLessThanEqualAndEndTimeGreaterThanAndFacilityIdAndConditionPriceId(
					LocalTime.parse(orderTimeDto.getStartTime()), LocalTime.parse(orderTimeDto.getStartTime()), request.getFacilityId(),
							conditionPrice).orElse(null);
						
			if(priceBySlot != null) {
				price = priceBySlot.getPrice();
			}
			totolPrice += price;
			Slot slot = createSlotInfo(date, order, LocalTime.parse(orderTimeDto.getStartTime()), 
					LocalTime.parse(orderTimeDto.getEndTime()), price);
			slots.add(slot);
		}
		
		//Create story
		Set<Story> stories = new HashSet<>();
		for(OrderStoryRequestDTO orderStoryDTO : request.getStories()) {
			if((orderStoryDTO.getTemplateId() == null || orderStoryDTO.getTemplateId().length() == 0) && (orderStoryDTO.getMessage() == null || orderStoryDTO.getMessage().length() == 0)) {
				throw new ResourceInvalidInputException(ErrorCodeEnum.STORY_ONLY_MESSAGE_OR_TEMPLATE, "Story must be has template or message.");
			}
			Story story = new Story(orderStoryDTO.getMessage(), orderStoryDTO.getPriority());
			if(orderStoryDTO.getTemplateId() != null && orderStoryDTO.getTemplateId().length() > 0) {
				Template template = templateRepository.findByActiveAndId(true, orderStoryDTO.getTemplateId())
						.orElseThrow(() -> new ResourceNotFoundException(ErrorCodeEnum.TEMPLATE_NOT_FOUND, "Template", "id", orderStoryDTO.getTemplateId()));
				story.setTemplate(template);
			}
			story.setFacility(facility);
			story.setOrder(order);
			stories.add(story);
		}
		
		order.setFacility(facility);	
		order.setSlots(slots);
		order.setStories(stories);
		order.setAmount(totolPrice);
		order.setCreatedBy(request.getFireBaseId());
		orderRepository.save(order);
		
		List<OrderStoryDTO> storyDTOs = new ArrayList<>();	
		storyRepository.findByOrderId(order.getId()).forEach(story -> {
			OrderStoryDTO storyDTO = new OrderStoryDTO();
			storyDTO.setMessage(story.getMessage());
			storyDTO.setPriority(story.getPriority());
			if(story.getTemplate() != null) {
				storyDTO.setTemplateName(story.getTemplate().getNameDsp());
				storyDTO.setImageURI(story.getTemplate().getImageUri());
				storyDTO.setDurationTemplate(story.getTemplate().getDuration());
				for (CategoryTranslate categoryTranslate : story.getTemplate().getCategory().getTranslates()) {
					if (categoryTranslate.getCode() == LanguageCode.VI) {
						storyDTO.setTemplateCategoryNameVi(categoryTranslate.getName());
					}
					if (categoryTranslate.getCode() == LanguageCode.EN) {
						storyDTO.setTemplateCategoryNameEn(categoryTranslate.getName());
					}
				}
			}
			storyDTOs.add(storyDTO);
		});
		
		MobileFacilityDTO mobileFacilityDTO = new MobileFacilityDTO();
		mobileFacilityDTO.setId(order.getFacility().getId());
		mobileFacilityDTO.setLocation(order.getFacility().getLocation());
				
		MobileOrderResDTO response = new MobileOrderResDTO(order.getId(), CommonFunctions.formatDoubleToString(order.getAmount())
				,order.getStatus().name(), order.getOrderNo(), CommonConstants.VNP_RESPONSE_CODE_DEFAULT ,mobileFacilityDTO, request.getTime(),storyDTOs);
			
		if(order.getType().equals(OrderType.B2C)) {
			//push notification
			PushNotificationEvent event = new PushNotificationEvent(this, request.getFireBaseId(), order);
			publisher.publishEvent(event);
		}
		
		return new BaseResponse(ScreenMessageConstants.SUCCESS, response);
	}

	public MobileTimeResDTO getStartAndEndTimeByDate(String dateInput, String facilityId) {
		logger.info("OrderService.getTimes");
		LocalDate date = LocalDate.parse(dateInput);
		LocalDate currentDate = LocalDate.now();
		if (date.isBefore(currentDate)) {
			throw new ResourceInvalidInputException(ErrorCodeEnum.DATA_INPUT_SMALLER_CURRENT_DATE, "Input Date must smaller than current Date ");
		}
		MobileTimeResDTO timeResponse = new MobileTimeResDTO();
		List<MobileTimeRejectResDTO> rejectResponseDTOs = new ArrayList<>();
		List<MobileTimeRejectResDTO> timeConfigRejectDTOs = new ArrayList<>();
		for (TimeConfig notUseTime : timeConfigRepository.findAllByTypeAndActive(TimeConfigType.NOT_USE, true)) {
			MobileTimeRejectResDTO responseDTO = new MobileTimeRejectResDTO();
			responseDTO.setTimeStart(notUseTime.getTimeStart().toString());
			responseDTO.setTimeEnd(notUseTime.getTimeEnd().toString());
			List<TimeConfig> specialTimes = timeConfigRepository.getTimeByDate(dateInput);
			if (specialTimes != null) {
				LocalDateTime timeNotUseStart = LocalDateTime.of(date, notUseTime.getTimeStart());
				LocalDateTime timeNotUseEnd = LocalDateTime.of(date, notUseTime.getTimeEnd());

				for (TimeConfig specialTime : specialTimes) {
					LocalDateTime timeSpecialStart = LocalDateTime.of(date, specialTime.getTimeStart());
					LocalDateTime timeSpecialEnd = LocalDateTime.of(date, specialTime.getTimeEnd());
					if (!checkIsPeriod(timeSpecialStart, timeNotUseStart, timeNotUseEnd)) {
						responseDTO.setTimeEnd(specialTime.getTimeStart().toString());
						if (timeSpecialEnd.compareTo(timeNotUseEnd) < 0) {
							MobileTimeRejectResDTO timeRejectBetweenEndSpecialAndEndNotUse = new MobileTimeRejectResDTO();
							timeRejectBetweenEndSpecialAndEndNotUse.setTimeStart(specialTime.getTimeEnd().toString());
							timeRejectBetweenEndSpecialAndEndNotUse.setTimeEnd(notUseTime.getTimeEnd().toString());
							rejectResponseDTOs.add(timeRejectBetweenEndSpecialAndEndNotUse);
						}
					}
				}
			}
			timeConfigRejectDTOs.add(responseDTO);
			rejectResponseDTOs.add(responseDTO);
		}

		List<OrderStatus> status = new ArrayList<OrderStatus>();
		status.add(OrderStatus.APPROVED);
		status.add(OrderStatus.PAID);
		status.add(OrderStatus.NEW);
		
		for (Slot slot : slotRepository.findByOrderFacilityIdAndActiveAndDateDisplayAndOrderStatusIn(facilityId, true, date, status)) {
			MobileTimeRejectResDTO responseDTO = new MobileTimeRejectResDTO();
			responseDTO.setTimeStart(slot.getTimeStart().toString());
			responseDTO.setTimeEnd(slot.getTimeEnd().toString());
			rejectResponseDTOs.add(responseDTO);
		}
		List<TimeResDTO> timeResDtos = getTime(rejectResponseDTOs, timeConfigRejectDTOs, date, facilityId);
		timeResDtos.removeIf(p -> (p.getStatus() == "0" || p.getPrice().equalsIgnoreCase(CommonFunctions.formatDoubleToString(CommonConstants.PRICE_DEFAULT))));
		timeResponse.setStep(CommonConstants.STEP_TIME.toString());
		timeResponse.setTimes(timeResDtos);
		return timeResponse;
	}

	private boolean checkIsPeriod(LocalDateTime timeInput, LocalDateTime timeStart, LocalDateTime timeEnd) {
		if (timeInput.compareTo(timeStart) >= 0 && timeInput.compareTo(timeEnd) < 0) {
			return false;
		}
		return true;
	}

	private boolean checkValidOrderTime(LocalDateTime timeStart, LocalDateTime timeEnd) {
		if (timeStart.compareTo(timeEnd) >= 0) {
			return false;
		}
		if (Duration.between(timeStart, timeEnd).toMinutes() % CommonConstants.STEP_TIME != 0) {
			return false;
		}
		if (timeStart.getMinute() % 5 != 0) {
			return false;
		}
		if (Duration.between(timeStart, timeEnd).toMinutes() != CommonConstants.STEP_TIME) {
			return false;
		}

		return true;
	}

	private int findQualityAndCheckOrderTime(String date, String start, String end, String facilityId) {
		LocalDate orderDate = LocalDate.parse(date);
		LocalDateTime timeStart = LocalDateTime.of(orderDate, LocalTime.parse(start));
		LocalDateTime timeEnd = LocalDateTime.of(orderDate, LocalTime.parse(end));

		if (!checkValidOrderTime(timeStart, timeEnd)) {
			throw new ResourceInvalidInputException(ErrorCodeEnum.ORDER_TIME_INVALID, "Order", "order time", "invalid");
		}

		for (MobileTimeRejectResDTO timeReject : getListTimeReject(date, facilityId)) {
			LocalDateTime timeRejectStart = LocalDateTime.of(orderDate, LocalTime.parse(timeReject.getTimeStart()));
			LocalDateTime timeRejectEnd = LocalDateTime.of(orderDate, LocalTime.parse(timeReject.getTimeEnd()));
			if (!checkIsPeriod(timeStart, timeRejectStart, timeRejectEnd)) {
				throw new ResourceInvalidInputException(ErrorCodeEnum.ORDER_TIME_INVALID, "Order", "order time", "is not suitable");
			}
		}
		
		if(LocalDate.now().equals(orderDate) && LocalTime.now().plusMinutes(30).isAfter(LocalTime.parse(start))) {
			throw new ResourceInvalidInputException(ErrorCodeEnum.ORDER_TIME_INVALID, "Order", "order time", "is not valid");
		}
		return (int) Duration.between(timeStart, timeEnd).toMinutes() / 5;
	}

	private List<TimeResDTO> getTime(List<MobileTimeRejectResDTO> rejectResponseDTOs,
		List<MobileTimeRejectResDTO> timeConfigRejectDTOs, LocalDate date, String facilityId) {
		List<PriceBySlot> priceBySlots = priceBySlotRepository.findAllByActive(true);
		List<TimeResDTO> timeResDtos = new ArrayList<>();
		for (int i = 0; i < 24; i++) {

			for (int j = 0; j < 60; j += CommonConstants.STEP_TIME) {
				TimeResDTO timeResDto = new TimeResDTO();
				LocalTime time = LocalTime.of(i, j);
				if(time.isAfter(LocalTime.now().plusMinutes(CommonConstants.TIME_DURATION_LIMT_ORDER))) {
					timeResDto.setTime(time.toString());
					timeResDto.setPrice(getPrice(priceBySlots, time, facilityId, date));
					
					LocalDateTime dateTime = LocalDateTime.of(date, LocalTime.parse(timeResDto.getTime()));
					DateTimeFormatter dateTimeFormatter = DateTimeFormatter
							.ofPattern(CommonConstants.DATE_YYYY_MM_DD_HH_MM);
					String stringDateTime = dateTime.format(dateTimeFormatter);
	
					timeResDto.setDateTime(stringDateTime);
	
					timeResDto.setStatus("1");
					for (MobileTimeRejectResDTO rejectResponseDTO : rejectResponseDTOs) {
						if (time.isAfter(LocalTime.parse(rejectResponseDTO.getTimeStart()))
								&& time.isBefore(LocalTime.parse(rejectResponseDTO.getTimeEnd()))
								|| time.equals(LocalTime.parse(rejectResponseDTO.getTimeStart()))) {
							timeResDto.setStatus("0");
						}
					}
					if (timeConfigRejectDTOs.size() != 0) {
						for (MobileTimeRejectResDTO timeConfigRejectDTO : timeConfigRejectDTOs) {
							LocalTime timeConfigRejectEnd = LocalTime.parse(timeConfigRejectDTO.getTimeEnd());
							if (time.isAfter(timeConfigRejectEnd) || time.equals(timeConfigRejectEnd)) {
								timeResDtos.add(timeResDto);
							}
						}
					} else {
						timeResDtos.add(timeResDto);
					}
				}
			}
		}
		return timeResDtos;
	}

	private List<MobileTimeRejectResDTO> mergeAndSortTimeReject(List<MobileTimeRejectResDTO> timeRejectRes) {
		Collections.sort(timeRejectRes);

		for (int i = 0; i < timeRejectRes.size(); i++) {
			for (int j = i + 1; j < timeRejectRes.size(); j++) {
				if (LocalTime.parse(timeRejectRes.get(i).getTimeEnd())
						.compareTo(LocalTime.parse(timeRejectRes.get(j).getTimeStart())) == 0) {
					timeRejectRes.get(i).setTimeEnd(timeRejectRes.get(j).getTimeEnd());
					timeRejectRes.remove(timeRejectRes.get(j));
				}
			}
		}

		return timeRejectRes;
	}

	private Slot createSlotInfo(LocalDate date, Order order, LocalTime timeStart, LocalTime timeEnd, Double unitPrice) {
		Slot slot = new Slot(timeStart, timeEnd, date, true);
		slot.setOrder(order);
		slot.setUnitPrice(unitPrice);
		slot.setCreatedAt(new Date());
		slot.setUpdatedAt(new Date());
		return slot;
	}
	
	private List<OrderTimeDTO> checkAndRemoveDuplicateSlot(List<OrderTimeDTO> orderTime) {		
		Set<String> result = new HashSet<>();
		List<OrderTimeDTO> orderTimeDistinct = orderTime.stream()
	            .filter(time -> result.add(time.getStartTime()))
	            .filter(time -> result.add(time.getEndTime()))
	            .collect(Collectors.toList());
		return orderTimeDistinct;
	}
	
	private String getPrice(List<PriceBySlot> priceBySlots, LocalTime time, String facilityId, LocalDate date) {
		String priceNormal = CommonFunctions.formatDoubleToString(CommonConstants.PRICE_DEFAULT);
		for(PriceBySlot priceBySlot : priceBySlots) {			
			if(!time.isBefore(priceBySlot.getStartTime()) && time.isBefore(priceBySlot.getEndTime()) 
					&& priceBySlot.getFacility().getId().equals(facilityId)) {
				 if(priceBySlot.getConditionPrice().getSpecialDate() != null
							&& date.getDayOfMonth() == priceBySlot.getConditionPrice().getSpecialDate().getDayOfMonth()
							&& date.getMonthValue() == priceBySlot.getConditionPrice().getSpecialDate().getMonthValue()) {
						return CommonFunctions.formatDoubleToString(priceBySlot.getPrice());
				}
				if((date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) 
						&& priceBySlot.getConditionPrice().getId().equalsIgnoreCase(ConditionPrice.WEEKEND.name())) {
					priceNormal = CommonFunctions.formatDoubleToString(priceBySlot.getPrice());
				}else if((date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY) 
						&& priceBySlot.getConditionPrice().getId().equalsIgnoreCase(ConditionPrice.NORMAL.name())) {
					priceNormal = CommonFunctions.formatDoubleToString(priceBySlot.getPrice());
				}
				
			} 
		}		
		return priceNormal;
	}
	
	private List<MobileTimeRejectResDTO> getListTimeReject(String dateInput, String facilityId) {
		LocalDate date = LocalDate.parse(dateInput);
		LocalDate currentDate = LocalDate.now();
		if (date.isBefore(currentDate)) {
			throw new ResourceInvalidInputException(ErrorCodeEnum.DATA_INPUT_SMALLER_CURRENT_DATE, "Input Date must smaller than current Date ");
		}
		List<MobileTimeRejectResDTO> rejectResponseDTOs = new ArrayList<>();
		for (TimeConfig notUseTime : timeConfigRepository.findAllByTypeAndActive(TimeConfigType.NOT_USE, true)) {
			MobileTimeRejectResDTO responseDTO = new MobileTimeRejectResDTO();
			responseDTO.setTimeStart(notUseTime.getTimeStart().toString());
			responseDTO.setTimeEnd(notUseTime.getTimeEnd().toString());
			List<TimeConfig> specialTimes = timeConfigRepository.getTimeByDate(dateInput);
			if (specialTimes != null) {
				LocalDateTime timeNotUseStart = LocalDateTime.of(date, notUseTime.getTimeStart());
				LocalDateTime timeNotUseEnd = LocalDateTime.of(date, notUseTime.getTimeEnd());

				for (TimeConfig specialTime : specialTimes) {
					LocalDateTime timeSpecialStart = LocalDateTime.of(date, specialTime.getTimeStart());
					LocalDateTime timeSpecialEnd = LocalDateTime.of(date, specialTime.getTimeEnd());
					if (!checkIsPeriod(timeSpecialStart, timeNotUseStart, timeNotUseEnd)) {
						responseDTO.setTimeEnd(specialTime.getTimeStart().toString());
						if (timeSpecialEnd.compareTo(timeNotUseEnd) < 0) {
							MobileTimeRejectResDTO timeRejectBetweenEndSpecialAndEndNotUse = new MobileTimeRejectResDTO();
							timeRejectBetweenEndSpecialAndEndNotUse.setTimeStart(specialTime.getTimeEnd().toString());
							timeRejectBetweenEndSpecialAndEndNotUse.setTimeEnd(notUseTime.getTimeEnd().toString());
							rejectResponseDTOs.add(timeRejectBetweenEndSpecialAndEndNotUse);
						}
					}
				}
			}
			rejectResponseDTOs.add(responseDTO);
		}
		List<OrderStatus> status = new ArrayList<OrderStatus>();
		status.add(OrderStatus.APPROVED);
		status.add(OrderStatus.PAID);
		status.add(OrderStatus.NEW);
		
		for (Slot slot : slotRepository.findByOrderFacilityIdAndActiveAndDateDisplayAndOrderStatusIn(facilityId, true, date, status)) {
			MobileTimeRejectResDTO responseDTO = new MobileTimeRejectResDTO();
			responseDTO.setTimeStart(slot.getTimeStart().toString());
			responseDTO.setTimeEnd(slot.getTimeEnd().toString());
			rejectResponseDTOs.add(responseDTO);
		}
		return mergeAndSortTimeReject(rejectResponseDTOs);
	}
	
	public BaseResponse getOrderByOrderIdAndFireBaseId(String fireBaseId, String orderId) {
		logger.info("OrderService.getOrderByOrderIdAndFireBaseId");
		Order order = orderRepository.findByActiveAndIdAndCreatedBy(true, orderId, fireBaseId).orElseThrow(
				() -> new ResourceNotFoundException(ErrorCodeEnum.ORDER_MISS_MATCH_WITH_USER_ID, "Order", "id, firebase id", orderId + ", " + fireBaseId));

		if(order.getType().equals(OrderType.B2B)) {
			throw new ResourceNotFoundException(ErrorCodeEnum.ORDER_TYPE_NOT_FOUND, "Order", "type", "B2C");
		}
		
		MobileOrderResDTO responseDTO = new MobileOrderResDTO();
		responseDTO.setId(order.getId());		
		responseDTO.setOrderNo(order.getOrderNo());
		responseDTO.setVnpResponseCode(order.getVnpResponseCode());
		responseDTO.setAmount(CommonFunctions.formatDoubleToString(order.getAmount()));
		responseDTO.setStatus(order.getStatus().toString());
		
		List<OrderTimeDTO> listOrderTimes = new ArrayList<>();
		for (Slot slot : slotRepository.findByOrderIdAndActive(order.getId(), true)) {
			listOrderTimes.add(new OrderTimeDTO(slot.getTimeStart().toString(), slot.getTimeEnd().toString(), slot.getDateDisplay().toString()));
		}
		responseDTO.setTimes(listOrderTimes);
		
		List<OrderStoryDTO> stories = new ArrayList<OrderStoryDTO>();
		for (Story story : order.getStories()) {
			OrderStoryDTO orderStoryDTO = new OrderStoryDTO();
			orderStoryDTO.setMessage(story.getMessage());
			orderStoryDTO.setPriority(story.getPriority());
			if(story.getTemplate() != null) {
				orderStoryDTO.setTemplateName(story.getTemplate().getNameDsp());
				orderStoryDTO.setImageURI(story.getTemplate().getImageUri());
				orderStoryDTO.setDurationTemplate(story.getTemplate().getDuration());
				for (CategoryTranslate categoryTranslate : story.getTemplate().getCategory().getTranslates()) {
					if (categoryTranslate.getCode() == LanguageCode.VI) {
						orderStoryDTO.setTemplateCategoryNameVi(categoryTranslate.getName());
					}
					if (categoryTranslate.getCode() == LanguageCode.EN) {
						orderStoryDTO.setTemplateCategoryNameEn(categoryTranslate.getName());
					}
				}				
			}	
			stories.add(orderStoryDTO);
		}
		responseDTO.setStories(stories);
		
		MobileFacilityDTO mobileFacilityDTO = new MobileFacilityDTO();
		mobileFacilityDTO.setId(order.getFacility().getId());
		mobileFacilityDTO.setLocation(order.getFacility().getLocation());
		for (FacilityTranslate facilityTranslate : order.getFacility().getTranslates()) {
			if (facilityTranslate.getCode() == LanguageCode.VI) {
				mobileFacilityDTO.setTitleVi(facilityTranslate.getTitle());
			}
			if (facilityTranslate.getCode() == LanguageCode.EN) {
				mobileFacilityDTO.setTitleEn(facilityTranslate.getTitle());
			}
		}
		responseDTO.setFacility(mobileFacilityDTO);
				
		return new BaseResponse(ScreenMessageConstants.SUCCESS, responseDTO);
	}
	
	public BaseResponse getAllOrderByReviewerId(String reviewerId, Integer pageNo, Integer pageSize) {
		logger.info("OrderService.getAllOrderByUserIdForMoble");
		try {
			Pageable pageable;
			List<Order> listOrders = new ArrayList<>();
			List<MobileOrderResDTO> mobileOrderResDTOs = new ArrayList<>();

			if (pageSize != null) {
				if (pageNo == null) {
					pageNo = 0;
					pageable = PageRequest.of(pageNo, pageSize, Sort.by("createdAt").descending());
				} else {
					pageable = PageRequest.of(pageNo, pageSize, Sort.by("createdAt").descending());
				}
				Page<Order> pagedResult = orderRepository.findByActiveAndCreatedBy(pageable, true, reviewerId);

				listOrders = pagedResult.getContent();
			} else {
				listOrders = orderRepository.findByActiveAndCreatedBy(true, reviewerId);
			}

			for (Order order : listOrders) {
				if (order.getType().equals(OrderType.B2C)) {
					MobileOrderResDTO responseDTO = new MobileOrderResDTO();
					responseDTO.setId(order.getId());
					responseDTO.setAmount(CommonFunctions.formatDoubleToString(order.getAmount()));
					responseDTO.setStatus(order.getStatus().toString());
					responseDTO.setOrderNo(order.getOrderNo());
					responseDTO.setVnpResponseCode(order.getVnpResponseCode());
					List<OrderTimeDTO> listOrderTimes = new ArrayList<>();
					for (Slot slot : slotRepository.findByOrderIdAndActive(order.getId(), true)) {
						listOrderTimes.add(new OrderTimeDTO(slot.getTimeStart().toString(), slot.getTimeEnd().toString(), slot.getDateDisplay().toString()));
					}
					responseDTO.setTimes(listOrderTimes);
					
					List<OrderStoryDTO> storyDTOs = new ArrayList<>();	
					storyRepository.findByOrderId(order.getId()).forEach(story -> {
						OrderStoryDTO storyDTO = new OrderStoryDTO();
						storyDTO.setMessage(story.getMessage());
						storyDTO.setPriority(story.getPriority());
						if(story.getTemplate() != null) {
							storyDTO.setTemplateName(story.getTemplate().getNameDsp());
							storyDTO.setImageURI(story.getTemplate().getImageUri());
							storyDTO.setDurationTemplate(story.getTemplate().getDuration());
							for (CategoryTranslate categoryTranslate : story.getTemplate().getCategory().getTranslates()) {
								if (categoryTranslate.getCode() == LanguageCode.VI) {
									storyDTO.setTemplateCategoryNameVi(categoryTranslate.getName());
								}
								if (categoryTranslate.getCode() == LanguageCode.EN) {
									storyDTO.setTemplateCategoryNameEn(categoryTranslate.getName());
								}
							}
						}
						storyDTOs.add(storyDTO);
					});
					
					responseDTO.setStories(storyDTOs);
					
					mobileOrderResDTOs.add(responseDTO);
				}
			}

			return new BaseResponse(ScreenMessageConstants.SUCCESS, mobileOrderResDTOs);
		} catch (Exception e) {
			logger.info(ScreenMessageConstants.FAILED, e);
			throw new ResourceInvalidInputException(ScreenMessageConstants.FAILED);
		}
	}
	
	public BaseResponse getOrderByOrderIdAndReviewerId(String reviewerId, String orderId) {
		logger.info("OrderService.getOrderByOrderIdAndUserId");
		Order order = orderRepository.findByActiveAndIdAndCreatedBy(true, orderId, reviewerId).orElseThrow(
				() -> new ResourceNotFoundException(ErrorCodeEnum.ORDER_MISS_MATCH_WITH_USER_ID, "Order", "id, user id", orderId + ", " + reviewerId));

		if(order.getType().equals(OrderType.B2B)) {
			throw new ResourceNotFoundException(ErrorCodeEnum.ORDER_TYPE_NOT_FOUND, "Order", "type", "B2C");
		}
		
		MobileOrderResDTO responseDTO = new MobileOrderResDTO();
		responseDTO.setId(order.getId());		
		responseDTO.setOrderNo(order.getOrderNo());
		responseDTO.setAmount(CommonFunctions.formatDoubleToString(order.getAmount()));
		responseDTO.setStatus(order.getStatus().toString());
		
		List<OrderTimeDTO> listOrderTimes = new ArrayList<>();
		for (Slot slot : slotRepository.findByOrderIdAndActive(order.getId(), true)) {
			listOrderTimes.add(new OrderTimeDTO(slot.getTimeStart().toString(), slot.getTimeEnd().toString(), slot.getDateDisplay().toString()));
		}
		responseDTO.setTimes(listOrderTimes);
		
		List<OrderStoryDTO> stories = new ArrayList<OrderStoryDTO>();
		for (Story story : order.getStories()) {
			OrderStoryDTO orderStoryDTO = new OrderStoryDTO();
			orderStoryDTO.setMessage(story.getMessage());
			orderStoryDTO.setPriority(story.getPriority());
			if(story.getTemplate() != null) {
				orderStoryDTO.setTemplateName(story.getTemplate().getNameDsp());
				orderStoryDTO.setImageURI(story.getTemplate().getImageUri());
				orderStoryDTO.setDurationTemplate(story.getTemplate().getDuration());
				for (CategoryTranslate categoryTranslate : story.getTemplate().getCategory().getTranslates()) {
					if (categoryTranslate.getCode() == LanguageCode.VI) {
						orderStoryDTO.setTemplateCategoryNameVi(categoryTranslate.getName());
					}
					if (categoryTranslate.getCode() == LanguageCode.EN) {
						orderStoryDTO.setTemplateCategoryNameEn(categoryTranslate.getName());
					}
				}				
			}
			stories.add(orderStoryDTO);
		}
		responseDTO.setStories(stories);
		
		return new BaseResponse(ScreenMessageConstants.SUCCESS, responseDTO);
	}
	
	public BaseResponse updateStatus(OrderReviewerUpdateRequest request) {
		logger.info("OrderService.update status");

		Order order = orderRepository.findByIdAndActive(request.getOrderId(), true)
				.orElseThrow(() -> new ResourceNotFoundException(ErrorCodeEnum.ORDER_ID_INVALID, "Order", "id", request.getOrderId()));
		order.setStatus(OrderStatus.fromValue(request.getStatus()));
		order.setComment(request.getComment());
		order.setUpdatedAt(new Date());
		orderRepository.save(order);
		
		//Push notification
		if(order.getType().equals(OrderType.B2C)) {
			//push notification
			PushNotificationEvent event = new PushNotificationEvent(this, order.getCreatedBy(), order);
			publisher.publishEvent(event);
		}
		
		return new BaseResponse(ScreenMessageConstants.SUCCESS, "Update order successfully!");
	}	
	
	public void cleanOrderTimeout() {
		logger.info("OrderService.cleanOrderTimeout");

		List<Order> orders = this.orderRepository.getOrderTimeout();

		if (orders != null && !orders.isEmpty()) {
			orders.forEach(order -> order.setStatus(OrderStatus.CANCELLED));
			this.orderRepository.saveAll(orders);
		}
	}
}