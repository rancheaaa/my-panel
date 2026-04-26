import request from "../../utils/request";

export function getDashboardData() {
	return request({
		url: "/monitor/dashboard/data",
		method: "get",
	});
}

export function getDashboardOverview() {
	return request({
		url: "/monitor/dashboard/overview",
		method: "get",
	});
}

export function getDashboardTrend(data) {
	return request({
		url: "/monitor/dashboard/trend",
		method: "post",
		data,
	});
}

export function getServiceInstances() {
	return request({
		url: "/monitor/dashboard/service-instances",
		method: "get",
	});
}
