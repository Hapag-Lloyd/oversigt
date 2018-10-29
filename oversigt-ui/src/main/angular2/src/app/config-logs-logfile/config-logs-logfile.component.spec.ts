import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { ConfigLogsLogfileComponent } from './config-logs-logfile.component';

describe('ConfigLogsLogfileComponent', () => {
  let component: ConfigLogsLogfileComponent;
  let fixture: ComponentFixture<ConfigLogsLogfileComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ ConfigLogsLogfileComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(ConfigLogsLogfileComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
