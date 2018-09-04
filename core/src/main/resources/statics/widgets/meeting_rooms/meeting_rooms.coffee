class Dashing.MeetingRooms extends Dashing.Widget
	onData: (data) ->
		node = $(@node)
		table = node.find('> .tableMeetingRooms')
		table.height(node.height() - table.position().top - table.outerHeight(true) + table.height())
