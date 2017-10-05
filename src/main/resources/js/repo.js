AJS.$(function ($) {
    var Room, Rooms, rooms, PageView, pageView, RoomListView;

    Room = Backbone.Model.extend({});
    Rooms = Backbone.Collection.extend({
        model:Room,
        initialize:function (rooms) {
            var checkedRooms = hcRoomIds.split(","),
                sortedRooms = _.sortBy(rooms, function (room) {
                    return room.name.toLowerCase();
                });
            return _.map(sortedRooms, function (room) {
                if (_.indexOf(checkedRooms, String(room.room_id)) > -1) {
                    room.checked = 'checked="checked"';
                } else {
                    room.checked = "";
                }
                return room;
            });

        },
        toObject:function () {
            return this.models.map(function (room) {
                return room.attributes
            })
        }
    });
    rooms = new Rooms(_.filter(hcRooms.rooms, function (room) { return !room.is_archived; }));

    RoomListView = Backbone.View.extend({
        el:$('#room-list'),
        template:_.template($('#rooms-tmpl').html()),
        initialize:function () {
            this.render();
        },
        render:function () {
            this.$el.html(this.template({rooms:rooms.toObject()}));
        }
    });

    PageView = Backbone.View.extend({
        el:$('#hipchat-form'),
        initialize:function () {
            var roomListView = new RoomListView;
        }

    });
    pageView = new PageView;
})
