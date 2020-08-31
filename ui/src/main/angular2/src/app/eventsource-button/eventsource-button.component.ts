import { Component, OnInit, Input } from '@angular/core';
import { getLinkForEventSource } from '../app.component';

@Component({
  selector: 'app-eventsource-button',
  templateUrl: './eventsource-button.component.html',
})
export class EventsourceButtonComponent implements OnInit {
  @Input() eventSourceId: string;
  @Input() showAsText = false;

  constructor() { }

  ngOnInit() {
  }

  getEventSourceLink(id: string): string {
    return getLinkForEventSource(id);
  }
}
