#Remote Reload
This widget has been loaded from [https://gist.github.com/jwalton/6616670](https://gist.github.com/jwalton/6616670)


So, you made a change to your dashboard, and now you have to run all over the building, plugging in a keyboard, and reloading the dashboard on the various TVs in your office.  What if you could reload all the dashboard from the comfort of your desk?

Add this widget to the very bottom of your dashboard, after the "gridster" div:

    <div class="gridster">
      <ul>
        <li data-row="1" data-col="1" data-sizex="1" data-sizey="1">
          <div data-view="Image" data-image="/benbria/loop.png" style="background-color:#666766"></div>
        </li>

        <!-- Blah blah blah - widgets go here -->

      </ul>
    </div>

    <!-- Special reload widget, doesn't display on the page. -->
    <div data-id="reload" data-view="RemoteReload" style="padding: 0px"></div>
    
Note the `style="padding: 0px"`; this is important to make the widget take up no space on the page.  Now you can use curl to remotely force all dashboards to reload their page:

    curl -d '{ "auth_token": "YOUR_AUTH_TOKEN" }' \http://mydashboard.server.com/widgets/reload
    
You can also install this across all your dashboards by modifying layout.erb:

    <div id="container">
      <%= yield %>
      <div data-id="reload" data-view="RemoteReload" style="padding: 0px"></div>
    </div>