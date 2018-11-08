import { Component, OnInit } from '@angular/core';
import { SystemService, OversigtEvent } from 'src/oversigt-client';

export class EventItem {
  event: OversigtEvent;
  visible: boolean;

  constructor(event: OversigtEvent) {
    this.event = event;
    this.visible = false;
  }
}

@Component({
  selector: 'app-config-events',
  templateUrl: './config-events.component.html',
  styleUrls: ['./config-events.component.css']
})
export class ConfigEventsComponent implements OnInit {
  events: EventItem[] = [];
  filter = '';

  constructor(
    private ss: SystemService,
  ) { }

  ngOnInit() {
    this.reloadEvents();
  }

  reloadEvents(): void {
    const _this = this;
    this.events = [];
    this.ss.getCachedEvents().subscribe(
      events => {
        _this.events = events.map(e => new EventItem(e));
      }
    );
  }
}
