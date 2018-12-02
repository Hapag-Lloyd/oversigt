import { Component, OnInit } from '@angular/core';
import { EventSourceInstanceInfo, EventSourceService } from 'src/oversigt-client';

@Component({
  selector: 'app-config-eventsources-list',
  templateUrl: './config-eventsources-list.component.html',
  styleUrls: ['./config-eventsources-list.component.css']
})
export class ConfigEventsourcesListComponent implements OnInit {
  eventSourceInfos: EventSourceInstanceInfo[] = [];

  constructor(
    private ess: EventSourceService,
  ) { }

  ngOnInit() {
    this.ess.listInstances().subscribe(
      infos => {
        this.eventSourceInfos = infos;
      },
      error => {
        console.error(error);
        alert(error);
        // TODO: Error handling
      }
    );
    // TODO: alle 5 minuten die Liste der EventSources aktualisieren
  }

}
