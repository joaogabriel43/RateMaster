import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-traffic-chart',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './traffic-chart.component.html',
  styleUrl: './traffic-chart.component.css'
})
export class TrafficChartComponent implements OnInit {
  dataPoints: any[] = [];

  ngOnInit() {
    this.generateMockData();
  }

  generateMockData() {
    // Generate some smooth mock data for the chart bars
    for(let i = 0; i < 24; i++) {
      const allowed = Math.floor(Math.random() * 60) + 20;
      const blocked = Math.floor(Math.random() * 20);
      
      this.dataPoints.push({
        time: `${i}:00`,
        allowedHeight: allowed,
        blockedHeight: blocked,
        total: allowed + blocked
      });
    }
  }
}
