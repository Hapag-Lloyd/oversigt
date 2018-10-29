import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { ConfigEventsourcesEventsourceComponent } from './config-eventsources-eventsource.component';

describe('ConfigEventsourcesEventsourceComponent', () => {
  let component: ConfigEventsourcesEventsourceComponent;
  let fixture: ComponentFixture<ConfigEventsourcesEventsourceComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ ConfigEventsourcesEventsourceComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(ConfigEventsourcesEventsourceComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
