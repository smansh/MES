var QCD = QCD || {};

QCD.dashboardContext = {};

QCD.dashboardContext.ordersPending = {};
QCD.dashboardContext.ordersInProgress = {};
QCD.dashboardContext.ordersCompleted = {};

QCD.dashboardContext.operationalTasksPending = {};
QCD.dashboardContext.operationalTasksInProgress = {};
QCD.dashboardContext.operationalTasksCompleted = {};

QCD.dashboardContext.getOrdersPending = function getOrdersPending() {
    return QCD.dashboardContext.ordersPending;
}
QCD.dashboardContext.getOrdersInProgress = function getOrdersInProgress() {
    return QCD.dashboardContext.ordersInProgress;
}
QCD.dashboardContext.getOrdersCompleted = function getOrdersCompleted() {
    return QCD.dashboardContext.ordersCompleted;
}

QCD.dashboardContext.getOperationalTasksPending = function getOperationalTasksPending() {
    return QCD.dashboardContext.operationalTasksPending;
}
QCD.dashboardContext.getOperationalTasksInProgress = function getOperationalTasksInProgress() {
    return QCD.dashboardContext.operationalTasksInProgress;
}
QCD.dashboardContext.getOperationalTasksCompleted = function getOperationalTasksCompleted() {
    return QCD.dashboardContext.operationalTasksCompleted;
}

var messagesController = new QCD.MessagesController();

QCD.dashboard = (function () {
	function init() {
	    initDailyProductionChart();
	    initOrders();
	    initOperationalTasks();

		registerChart();
		registerButtons();
		registerKanban();
	}

	function registerChart() {
        if ($('#dashboardChart').length) {
            Chart.platform.disableCSSInjection = true;

            Chart.plugins.register({
                afterDraw: function (chart) {
                    if (chart.data.datasets[0].data[0] === 0 && chart.data.datasets[0].data[1] === 0 && chart.data.datasets[0].data[2] === 0) {
                        let ctx = chart.chart.ctx;
                        let width = chart.chart.width;
                        let height = chart.chart.height
                        chart.clear();

                        ctx.save();
                        ctx.textAlign = 'center';
                        ctx.textBaseline = 'middle';
                        ctx.font = "16px";
                        ctx.fillText(QCD.translate('basic.dashboard.dailyProductionChart.noData'), width / 2, height / 2);
                        ctx.restore();
                    }
                }
            });
        }
    }

    function initDailyProductionChart() {
        if ($('#dashboardChart').length) {
            $.get("/rest/dailyProductionChart/data",
                function (data) {
                    new Chart('chart', {
                        type: 'pie',
                        data: {
                            datasets: [{
                                data: data,
                                borderWidth: 0,
                                backgroundColor: [
                                    '#C7D1D9',
                                    '#D9AFA0',
                                    '#639AA6'
                                ]
                            }],
                            labels: [
                                QCD.translate('basic.dashboard.dailyProductionChart.pending.label'),
                                QCD.translate('basic.dashboard.dailyProductionChart.inProgress.label'),
                                QCD.translate('basic.dashboard.dailyProductionChart.done.label')
                            ]
                        },
                        options: {
                            title: {
                                display: true,
                                text: QCD.translate('basic.dashboard.dailyProductionChart.header'),
                                fontSize: 16,
                                fontFamily: '"Helvetica Neue"',
                                fontColor: 'black'
                            },
                            legend: {
                                position: 'bottom',
                                labels: {
                                    fontColor: 'black'
                                }
                            }
                        }
                    });
                }
            );
        }
    }

    function registerButtons() {
        if ($('#dashboardButtons').length) {
            $("#dashboardButtons .card").each(function(index, element){
                $(this).fadeIn((index + 1) * 250);
            });

            $("#dashboardButtons .card").hover(
                function() {
                    $(this).removeClass('bg-secondary').addClass('shadow-sm').addClass('bg-success').addClass('card-hover');
                }, function() {
                    $(this).addClass('bg-secondary').removeClass('shadow-sm').removeClass('bg-success').removeClass('card-hover');
                }
            );
        }
    }

    function registerKanban() {
        if ($('#dashboardKanban #ordersPending').length) {
            $.each(QCD.dashboardContext.getOrdersPending(), function (i, order) {
                appendOrder('ordersPending', order);
            });
            $.each(QCD.dashboardContext.getOrdersInProgress(), function (i, order) {
                appendOrder('ordersInProgress', order);
            });
            $.each(QCD.dashboardContext.getOrdersCompleted(), function (i, order) {
                appendOrder('ordersCompleted', order);
            });
            updateDropzones();
        }
        if ($('#dashboardKanban #operationalTasksPending').length) {
            $.each(QCD.dashboardContext.getOperationalTasksPending(), function (i, operationalTask) {
                appendOperationalTask('operationalTasksPending', operationalTask);
            });
            $.each(QCD.dashboardContext.getOperationalTasksInProgress(), function (i, operationalTask) {
                appendOperationalTask('operationalTasksInProgress', operationalTask);
            });
            $.each(QCD.dashboardContext.getOperationalTasksCompleted(), function (i, operationalTask) {
                appendOperationalTask('operationalTasksCompleted', operationalTask);
            });
        }

        $("#dashboardKanban .card.bg-light").each(function(index, element){
            $(this).fadeIn((index + 1) * 250);
        });

        $("#dashboardKanban .items .card").hover(
            function() {
                $(this).addClass('shadow-sm');
            }, function() {
                $(this).removeClass('shadow-sm');
            }
        );
    }

    function appendOrder(ordersType, order) {
        $('#' + ordersType).append(
            createOrderDiv(order)
        );
    }

    function prependOrder(ordersType, order) {
        $('#' + ordersType).prepend(
            createOrderDiv(order)
        );
    }

    function appendOperationalTask(operationalTasksType, operationalTask) {
        let doneInPercent = Math.round(operationalTask.usedQuantity * 100 / operationalTask.plannedQuantity);

        operationalTask.usedQuantity = operationalTask.usedQuantity ? operationalTask.usedQuantity : 0;

        $('#' + operationalTasksType).append(
            '<div class="card" id="operationalTask' + operationalTask.id + '">' +
                '<div class="card-header bg-secondary py-2">' +
                    '<a href="#" class="card-title text-white" onclick="goToOperationalTaskDetails(' + operationalTask.id + ')">' + operationalTask.number + '</a>' +
                '</div>' +
                 '<div class="card-body py-2">' +
                     '<span class="font-weight-bold">' + QCD.translate("basic.dashboard.operationalTasks.name.label") + ':</span> ' + operationalTask.name + '<br/>' +
                     ((operationalTask.type == "02executionOperationInOrder" && operationalTask.orderNumber) ? '<span class="font-weight-bold">' + QCD.translate("basic.dashboard.operationalTasks.orderNumber.label") + ':</span> <a href="#" onclick="goToOrderDetails(' + operationalTask.orderId + ')">' + operationalTask.orderNumber + '</a><br/>' : '') +
                     (operationalTask.workstationNumber ? '<span class="font-weight-bold">' + QCD.translate("basic.dashboard.operationalTasks.workstationNumber.label") + ':</span> ' + operationalTask.workstationNumber + '<br/>' : '') +
                     ((operationalTask.type == "02executionOperationInOrder" && operationalTask.orderProductNumber) ? '<span class="font-weight-bold">' + QCD.translate("basic.dashboard.operationalTasks.orderProductNumber.label") + ':</span> ' + operationalTask.orderProductNumber + '<br/>' : '') +
                     ((operationalTask.type == "02executionOperationInOrder" && operationalTask.productNumber) ? '<span class="font-weight-bold">' + QCD.translate("basic.dashboard.operationalTasks.productNumber.label") + ':</span> ' + operationalTask.productNumber + '<br/>' : '') +
                     ((operationalTask.type == "02executionOperationInOrder" && operationalTask.plannedQuantity && operationalTask.productUnit) ? '<span class="float-left"><span class="font-weight-bold">' + QCD.translate("basic.dashboard.operationalTasks.plannedQuantity.label") + ':</span> ' + operationalTask.plannedQuantity + ' ' + operationalTask.productUnit + '</span>' : '') +
                     ((operationalTask.type == "02executionOperationInOrder" && operationalTasksType != 'operationalTasksPending') ? '<span class="float-right"><span class="font-weight-bold">' + QCD.translate("basic.dashboard.operationalTasks.usedQuantity.label") + ':</span> ' + operationalTask.usedQuantity + ' ' + operationalTask.productUnit + '</span>' : '') +
                     ((operationalTask.type == "02executionOperationInOrder" && operationalTask.plannedQuantity) ? '<br/>' : '') +
                     (operationalTask.staffName ? '<span class="font-weight-bold">' + QCD.translate("basic.dashboard.operationalTasks.staffName.label") + ':</span> ' + operationalTask.staffName + '<br/>' : '') +
                     ((operationalTask.type == "02executionOperationInOrder" && operationalTask.state == "02started") ? '<a href="#" class="badge badge-success float-right" onclick="goToProductionTrackingTerminal(null, ' + operationalTask.id + ', ' + (operationalTask.workstationNumber ? '\'' + operationalTask.workstationNumber + '\'' : null) + ')">' + QCD.translate("basic.dashboard.operationalTasks.showTerminal.label") + '</a>' : '') +
                 '</div>' +
                 (operationalTask.type == "02executionOperationInOrder" ? '<div class="card-footer">' + '<div class="progress">' + '<div class="progress-bar progress-bar-striped bg-info" role="progressbar" style="width: ' + doneInPercent + '%;" aria-valuenow="' + doneInPercent + '" aria-valuemin="0" aria-valuemax="100">' + doneInPercent + '%</div>' + '</div>' + '</div>' : '') +
            '</div><div> &nbsp; </div>'
        );
    }

    function prependOperationalTask(operationalTasksType, operationalTask) {
        let doneInPercent = Math.round(operationalTask.usedQuantity * 100 / operationalTask.plannedQuantity);

        operationalTask.usedQuantity = operationalTask.usedQuantity ? operationalTask.usedQuantity : 0;

        $('#' + operationalTasksType).prepend(
            '<div class="card" id="operationalTask' + operationalTask.id + '">' +
                '<div class="card-header bg-secondary py-2">' +
                    '<a href="#" class="card-title text-white" onclick="goToOperationalTaskDetails(' + operationalTask.id + ')">' + operationalTask.number + '</a>' +
                '</div>' +
                 '<div class="card-body py-2">' +
                     '<span class="font-weight-bold">' + QCD.translate("basic.dashboard.operationalTasks.name.label") + ':</span> ' + operationalTask.name + '<br/>' +
                     ((operationalTask.type == "02executionOperationInOrder" && operationalTask.orderNumber) ? '<span class="font-weight-bold">' + QCD.translate("basic.dashboard.operationalTasks.orderNumber.label") + ':</span> <a href="#" onclick="goToOrderDetails(' + operationalTask.orderId + ')">' + operationalTask.orderNumber + '</a><br/>' : '') +
                     (operationalTask.workstationNumber ? '<span class="font-weight-bold">' + QCD.translate("basic.dashboard.operationalTasks.workstationNumber.label") + ':</span> ' + operationalTask.workstationNumber + '<br/>' : '') +
                     ((operationalTask.type == "02executionOperationInOrder" && operationalTask.orderProductNumber) ? '<span class="font-weight-bold">' + QCD.translate("basic.dashboard.operationalTasks.orderProductNumber.label") + ':</span> ' + operationalTask.orderProductNumber + '<br/>' : '') +
                     ((operationalTask.type == "02executionOperationInOrder" && operationalTask.productNumber) ? '<span class="font-weight-bold">' + QCD.translate("basic.dashboard.operationalTasks.productNumber.label") + ':</span> ' + operationalTask.productNumber + '<br/>' : '') +
                     ((operationalTask.type == "02executionOperationInOrder" && operationalTask.plannedQuantity && operationalTask.productUnit) ? '<span class="float-left"><span class="font-weight-bold">' + QCD.translate("basic.dashboard.operationalTasks.plannedQuantity.label") + ':</span> ' + operationalTask.plannedQuantity + ' ' + operationalTask.productUnit + '</span>' : '') +
                     ((operationalTask.type == "02executionOperationInOrder" && operationalTasksType != 'operationalTasksPending') ? '<span class="float-right"><span class="font-weight-bold">' + QCD.translate("basic.dashboard.operationalTasks.usedQuantity.label") + ':</span> ' + operationalTask.usedQuantity + ' ' + operationalTask.productUnit + '</span>' : '') +
                     ((operationalTask.type == "02executionOperationInOrder" && operationalTask.plannedQuantity) ? '<br/>' : '') +
                     (operationalTask.staffName ? '<span class="font-weight-bold">' + QCD.translate("basic.dashboard.operationalTasks.staffName.label") + ':</span> ' + operationalTask.staffName + '<br/>' : '') +
                     ((operationalTask.type == "02executionOperationInOrder" && operationalTask.state == "02started") ? '<a href="#" class="badge badge-success float-right" onclick="goToProductionTrackingTerminal(null, ' + operationalTask.id + ', ' + (operationalTask.workstationNumber ? '\'' + operationalTask.workstationNumber + '\'' : null) + ')">' + QCD.translate("basic.dashboard.operationalTasks.showTerminal.label") + '</a>' : '') +
                 '</div>' +
                 (operationalTask.type == "02executionOperationInOrder" ? '<div class="card-footer">' + '<div class="progress">' + '<div class="progress-bar progress-bar-striped bg-info" role="progressbar" style="width: ' + doneInPercent + '%;" aria-valuenow="' + doneInPercent + '" aria-valuemin="0" aria-valuemax="100">' + doneInPercent + '%</div>' + '</div>' + '</div>' : '') +
            '</div><div> &nbsp; </div>'
        );
    }

    function initOrders() {
        if ($('#dashboardKanban #ordersPending').length) {
            getOrdersPending();
            getOrdersInProgress();
            getOrdersCompleted();
        }
    }

    function getOrdersPending() {
        $.ajax({
            url : "/rest/dashboardKanban/ordersPending",
            type : "GET",
            async : false,
            beforeSend : function() {
                //$("#loader").modal('show');
            },
            success : function(data) {
                QCD.dashboardContext.ordersPending = data;
            },
            error : function(data) {
                console.log("error")
            },
            complete : function() {
                //$("#loader").modal('hide');
            }
        });
    }

    function getOrdersInProgress() {
        $.ajax({
            url : "/rest/dashboardKanban/ordersInProgress",
            type : "GET",
            async : false,
            beforeSend : function() {
                //$("#loader").modal('show');
            },
            success : function(data) {
                QCD.dashboardContext.ordersInProgress = data;
            },
            error : function(data) {
                console.log("error")
            },
            complete : function() {
                //$("#loader").modal('hide');
            }
        });
    }

    function getOrdersCompleted() {
        $.ajax({
            url : "/rest/dashboardKanban/ordersCompleted",
            type : "GET",
            async : false,
            beforeSend : function() {
                //$("#loader").modal('show');
            },
            success : function(data) {
                QCD.dashboardContext.ordersCompleted = data;
            },
            error : function(data) {
                console.log("error")
            },
            complete : function() {
                //$("#loader").modal('hide');
            }
        });
    }

    function initOperationalTasks() {
        if ($('#dashboardKanban #operationalTasksPending').length) {
            getOperationalTasksPending();
            getOperationalTasksInProgress();
            getOperationalTasksCompleted();
        }
    }

    function getOperationalTasksPending() {
        $.ajax({
            url : "/rest/dashboardKanban/operationalTasksPending",
            type : "GET",
            async : false,
            beforeSend : function() {
                //$("#loader").modal('show');
            },
            success : function(data) {
                QCD.dashboardContext.operationalTasksPending = data;
            },
            error : function(data) {
                console.log("error")
            },
            complete : function() {
                //$("#loader").modal('hide');
            }
        });
    }

    function getOperationalTasksInProgress() {
        $.ajax({
            url : "/rest/dashboardKanban/operationalTasksInProgress",
            type : "GET",
            async : false,
            beforeSend : function() {
                //$("#loader").modal('show');
            },
            success : function(data) {
                QCD.dashboardContext.operationalTasksInProgress = data;
            },
            error : function(data) {
                console.log("error")
            },
            complete : function() {
                //$("#loader").modal('hide');
            }
        });
    }

    function getOperationalTasksCompleted() {
        $.ajax({
            url : "/rest/dashboardKanban/operationalTasksCompleted",
            type : "GET",
            async : false,
            beforeSend : function() {
                //$("#loader").modal('show');
            },
            success : function(data) {
                QCD.dashboardContext.operationalTasksCompleted = data;
            },
            error : function(data) {
                console.log("error")
            },
            complete : function() {
                //$("#loader").modal('hide');
            }
        });
    }

	return {
		init: init,
		initOrders: initOrders,
		appendOrder: appendOrder,
		prependOrder: prependOrder,
		prependOperationalTask: prependOperationalTask
	};

})();

$(document).ready(function() {
    QCD.dashboard.init();
    console.log(QCD.wizardToOpen);
    if(QCD.wizardToOpen) {
        if(QCD.wizardToOpen == 'orders') {
            addOrder();
        } else {
            addOperationalTask();
        }
    }
});

const drag = (event) => {
    event.dataTransfer.setData("text/plain", event.target.id);
    event.dataTransfer.setData(event.target.id, '');
}

const drop = (event) => {
    event.preventDefault();

    const data = event.dataTransfer.getData("text/plain");
    const orderId = data.replace('order', '');
    const element = document.querySelector(`#${data}`);

    $.ajax({
        url: "/rest/dashboardKanban/updateOrderState/" + orderId,
        type: "PUT",
        async: false,
        beforeSend: function () {
            // $("#loader").modal('show');
        },
        success: function (response) {
            if (response.message) {
                window.parent.addMessage({
                    type: 'failure',
                    title: QCD.translate('basic.dashboard.orderStateChange.error'),
                    content: response.message
                });
                removeClass(event.target, "droppable");
            } else {
                const doc = new DOMParser().parseFromString(createOrderDiv(response.order), 'text/html');
                try {
                    element.remove();
                    event.target.removeChild(event.target.firstChild);
                    event.target.appendChild(doc.body.firstChild);

                    unwrap(event.target);
                } catch (error) {
                    console.warn("can't move the item to the same place")
                }

                updateDropzones();
            }
        },
        error: function () {
            console.log("error")
            removeClass(event.target, "droppable");
        },
        complete: function () {
            // $("#loader").modal('hide');
        }
    });
}

function createOrderDiv(order) {
    let doneInPercent = Math.round(order.doneQuantity * 100 / order.plannedQuantity);

    order.doneQuantity = order.doneQuantity ? order.doneQuantity : 0;

    return '<div class="card draggable" id="order' + order.id + '" draggable="true" ondragstart="drag(event)">' +
        '<div class="card-header bg-secondary py-2">' +
        '<a href="#" class="card-title text-white" onclick="goToOrderDetails(' + order.id + ')">' + order.number + '</a>' +
        '</div>' +
        '<div class="card-body py-2">' +
        (order.productionLineNumber ? '<span class="font-weight-bold">' + QCD.translate("basic.dashboard.orders.productionLineNumber.label") + ':</span> ' + order.productionLineNumber + '<br/>' : '') +
        (order.productNumber ? '<span class="font-weight-bold">' + QCD.translate("basic.dashboard.orders.productNumber.label") + ':</span> ' + order.productNumber + '<br/>' : '') +
        ((order.plannedQuantity && order.productUnit) ? '<span class="float-left"><span class="font-weight-bold">' + QCD.translate("basic.dashboard.orders.plannedQuantity.label") + ':</span> ' + order.plannedQuantity + ' ' + order.productUnit + '</span>' : '') +
        ((order.state == "03inProgress" || order.state == "04completed") ? '<span class="float-right"><span class="font-weight-bold">' + QCD.translate("basic.dashboard.orders.doneQuantity.label") + ':</span> ' + order.doneQuantity + ' ' + order.productUnit + '</span>' : '') +
        (order.plannedQuantity ? '<br/>' : '') +
        (order.companyName ? '<span class="font-weight-bold">' + QCD.translate("basic.dashboard.orders.companyName.label") + ':</span> ' + order.companyName + '<br/>' : '') +
        (order.masterOrderNumber ? '<span class="font-weight-bold">' + QCD.translate("basic.dashboard.orders.masterOrderNumber.label") + ':</span> ' + order.masterOrderNumber + '<br/>' : '') +
        ((order.state == "03inProgress" && order.typeOfProductionRecording == "02cumulated") ? '<a href="#" class="badge badge-success float-right" onclick="goToProductionTrackingTerminal(' + order.id + ', null, null)">' + QCD.translate("basic.dashboard.orders.showTerminal.label") + '</a>' : '') +
        '</div>' +
        '<div class="card-footer">' + '<div class="progress">' + '<div class="progress-bar progress-bar-striped bg-info" role="progressbar" style="width: ' + doneInPercent + '%;" aria-valuenow="' + doneInPercent + '" aria-valuemin="0" aria-valuemax="100">' + doneInPercent + '%</div>' + '</div>' + '</div>' +
        '</div>';
}

const allowDrop = (event) => {
    let path = event.composedPath();
    if (hasClass(event.target, "dropzone")
        && (path[1].id === 'ordersInProgress' && document.getElementById(event.dataTransfer.types[1]).parentElement.id === 'ordersPending'
            || path[1].id === 'ordersCompleted' && document.getElementById(event.dataTransfer.types[1]).parentElement.id === 'ordersInProgress'
        )) {
        event.preventDefault();
        addClass(event.target, "droppable");
    }
}

const clearDrop = (event) => {
    removeClass(event.target, "droppable");
}

const updateDropzones = () => {
    let dropzone = $('<div class="dropzone rounded" ondrop="drop(event)" ondragover="allowDrop(event)" ondragleave="clearDrop(event)"> &nbsp; </div>');

    $('.dropzone').remove();

    dropzone.insertAfter('.card.draggable');

    $(".items:not(:has(.card.draggable))").append(dropzone);
};

function hasClass(target, className) {
    return new RegExp("(\\s|^)" + className + "(\\s|$)").test(target.className);
}

function addClass(element, className) {
    if (!hasClass(element, className)) {
        element.className += " " + className;
    }
}

function removeClass(element, className) {
    if (hasClass(element, className)) {
        var reg = new RegExp("(\\s|^)" + className + "(\\s|$)");

        element.className = element.className.replace(reg, " ");
    }
}

function unwrap(node) {
    node.replaceWith(...node.childNodes);
}

function goToMenuPosition(position) {
    if (window.parent.goToMenuPosition) {
        window.parent.goToMenuPosition(position);
    } else {
        window.location = "/main.html"
    }
}

function goToPage(url, isPage) {
    url = window.parent.encodeParams(url);
    if (window.parent.goToPage) {
        window.parent.goToPage(url, null, isPage);
    } else {
        window.location = "/main.html"
    }
}

function addOrder() {
    QCD.orderDefinitionWizard.init();
}

function addOperationalTask() {
    QCD.operationalTasksDefinitionWizard.init();
}

function goToOrderDetails(id) {
    goToPage("orders/orderDetails.html?context=" + JSON.stringify({
        "form.id": id,
        "form.undefined": null
    }), true);
}

function goToOperationalTaskDetails(id) {
    goToPage("orders/operationalTaskDetails.html?context=" + JSON.stringify({
        "form.id": id,
        "form.undefined": null
    }), true);
}

function goToProductionTrackingTerminal(orderId, operationalTaskId, workstationNumber) {
    let url = "/productionRegistrationTerminal.html";
    if (orderId) {
        url += "?orderId=" + orderId;
    } else if (operationalTaskId) {
        url += "?operationalTaskId=" + operationalTaskId;
        if (workstationNumber) {
            url += '&workstationNumber=' + workstationNumber;
        }
    }
    goToPage(url, false);
}

	function showMessage(type, title, content, autoClose) {
		messagesController.addMessage({
			type : type,
			title : title,
			content : content,
			autoClose : autoClose,
			extraLarge : false
		});
	}

function logoutIfSessionExpired(data) {
	if ($.trim(data) == "sessionExpired" || $.trim(data).substring(0, 20) == "<![CDATA[ERROR PAGE:") {
		window.location = "/login.html?timeout=true";
	}
}
