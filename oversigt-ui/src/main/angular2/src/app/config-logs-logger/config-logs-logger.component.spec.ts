import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { ConfigLogsLoggerComponent } from './config-logs-logger.component';

describe('ConfigLogsLoggerComponent', () => {
  let component: ConfigLogsLoggerComponent;
  let fixture: ComponentFixture<ConfigLogsLoggerComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ ConfigLogsLoggerComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(ConfigLogsLoggerComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
